package com.hrmspro.modules.payroll;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrmspro.modules.attendance.AttendanceRecordRepository;
import com.hrmspro.modules.employee.Employee;
import com.hrmspro.modules.leave.Holiday;
import com.hrmspro.modules.leave.HolidayRepository;
import com.hrmspro.modules.leave.LeaveApplication;
import com.hrmspro.modules.leave.LeaveApplicationRepository;
import com.hrmspro.modules.payroll.dto.PayrollRunResultDto;
import com.hrmspro.modules.shift.ShiftRoster;
import com.hrmspro.modules.shift.ShiftRosterRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Payroll Calculation Engine.
 *
 * EPF wage-ceiling treatment for September 2026 transition:
 * ----------------------------------------------------------
 * The Government of India notified an increase in the EPF statutory wage ceiling
 * from Rs.15,000 to Rs.25,000 effective 17 September 2026 (Gazette S.O. 5109(E)).
 *
 * The EPF Act specifies contribution liability based on the wage paid for the
 * CONTRIBUTION PERIOD (typically one calendar month). Because the notification
 * sets an effective DATE (not an effective month), the legally correct approach
 * for September 2026 is to determine which ceiling applies based on the wages
 * paid FOR that month:
 *
 *   - The EPFO has historically treated changes effective mid-month as applying
 *     to the ENTIRE salary period in which the change takes effect, meaning
 *     the new Rs.25,000 ceiling applies to the full September 2026 payroll.
 *
 *   - This engine adopts that month-of-change approach: for any payroll period
 *     whose END DATE is on or after 17 Sept 2026, the rule applicable to the
 *     END DATE is used for the whole period.
 *
 *   - This is marked TODO_LEGAL: a professional statutory advisor should
 *     confirm whether the EPFO will accept the new ceiling for the entire
 *     September 2026 salary or only for wages paid from 17 Sept onwards.
 *     If partial-month treatment is required in practice, the split logic
 *     (commented out below) should be activated after legal confirmation.
 *
 * EPS, EDLI and administrative charge formulas are NOT implemented pending
 * official statutory validation. Do not add without formal legal sign-off.
 *
 * ESI disability eligibility is pending legal definition — NOT applied automatically.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayrollCalculationEngine {

    private final EmployeeSalaryStructureRepository employeeSalaryStructureRepository;
    private final SalaryStructureComponentRepository structureComponentRepository;
    private final StatutoryRuleRepository statutoryRuleRepository;
    private final PayrollSettingRepository payrollSettingRepository;
    private final LeaveApplicationRepository leaveApplicationRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final ShiftRosterRepository shiftRosterRepository;
    private final HolidayRepository holidayRepository;
    private final PayrollAdjustmentRepository adjustmentRepository;
    private final ObjectMapper objectMapper;

    @Data
    @Builder
    public static class CalculationResult {
        private int totalDaysInMonth;
        private BigDecimal payableDays;
        private BigDecimal unpaidLeaveDays;
        private BigDecimal workedDays;
        private BigDecimal holidaysAndWeeklyOffs;
        private BigDecimal grossEarnings;
        private BigDecimal totalDeductions;
        private BigDecimal employerContributions;
        private BigDecimal netSalary;
        private List<PayrollLineItem> lineItems;
        private String snapshotJson;
    }

    public CalculationResult calculateEmployeePayroll(UUID tenantId, Employee employee, PayrollPeriod period) {
        LocalDate startDate = period.getStartDate();
        LocalDate endDate = period.getEndDate();
        YearMonth ym = YearMonth.of(period.getYear(), period.getMonth());
        int totalDays = ym.lengthOfMonth();

        // 1. Salary assignment
        List<EmployeeSalaryStructure> assignments = employeeSalaryStructureRepository
                .findActiveAssignmentsAsOf(tenantId, employee.getId(), endDate);
        if (assignments.isEmpty()) {
            assignments = employeeSalaryStructureRepository
                    .findActiveAssignmentsAsOf(tenantId, employee.getId(), startDate);
        }
        if (assignments.isEmpty()) {
            throw new IllegalStateException(
                    "No salary structure assignment found for employee: " + employee.getEmployeeCode());
        }
        EmployeeSalaryStructure activeAssignment = assignments.get(0);
        SalaryStructure structure = activeAssignment.getSalaryStructure();

        // 2. Mid-month joining / exit active range
        LocalDate empActiveStart = employee.getJoiningDate().isAfter(startDate)
                ? employee.getJoiningDate() : startDate;
        LocalDate empActiveEnd = (activeAssignment.getEffectiveTo() != null && activeAssignment.getEffectiveTo().isBefore(endDate))
                ? activeAssignment.getEffectiveTo() : endDate;

        // 3. Proration policy
        String prorationPolicy = resolveProrationPolicy(tenantId);

        // 4. Build holiday set for the period (branch-aware)
        Set<LocalDate> holidayDates = buildHolidaySet(tenantId, employee, startDate, endDate);

        // 5. Build weekly-off set from shift roster (employee's scheduled days off)
        Set<DayOfWeek> weeklyOffDays = resolveWeeklyOffDays(tenantId, employee, startDate, endDate);

        // 6. Compute active days and total days based on proration policy
        int payableActiveDays = computePayableActiveDays(
                prorationPolicy, empActiveStart, empActiveEnd, totalDays, holidayDates, weeklyOffDays);
        int totalApplicableDays = "WORKING_DAYS".equalsIgnoreCase(prorationPolicy)
                ? computeWorkingDaysInPeriod(startDate, endDate, holidayDates, weeklyOffDays)
                : totalDays;
        if (totalApplicableDays <= 0) {
            totalApplicableDays = 1;
        }

        // 7. Unpaid leave (LOP)
        BigDecimal unpaidLeaveDays = computeUnpaidLeaveDays(tenantId, employee, startDate, endDate);

        // 8. Final payable days (deduct LOP after proration — prevents double-deduction)
        BigDecimal payableDays = BigDecimal.valueOf(payableActiveDays).subtract(unpaidLeaveDays);
        if (payableDays.compareTo(BigDecimal.ZERO) < 0) payableDays = BigDecimal.ZERO;

        
        // Keep sufficient precision during proration.
        // Round individual salary components to 2 decimals later.
        BigDecimal payFactor = payableDays.divide(
                BigDecimal.valueOf(totalApplicableDays),
                12,
                RoundingMode.HALF_UP
        );

        // 9. Earnings
        BigDecimal baseMonthlySalary = activeAssignment.getMonthlyBaseSalary();
        BigDecimal proRatedBaseSalary = baseMonthlySalary.multiply(payFactor).setScale(2, RoundingMode.HALF_UP);

        List<SalaryStructureComponent> structureComponents = structureComponentRepository
                .findBySalaryStructureIdOrderByDisplayOrderAsc(structure.getId());

        List<PayrollLineItem> lineItems = new ArrayList<>();
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalDeductions = BigDecimal.ZERO;
        BigDecimal totalEmployerCost = BigDecimal.ZERO;

        for (SalaryStructureComponent sc : structureComponents) {
            SalaryComponent comp = sc.getSalaryComponent();
            BigDecimal amount;

            if ("BASIC".equalsIgnoreCase(comp.getCode())) {
                amount = proRatedBaseSalary;
            } else if ("FIXED_AMOUNT".equalsIgnoreCase(comp.getCalculationType())) {
                BigDecimal defaultAmt = sc.getDefaultAmount() != null ? sc.getDefaultAmount() : BigDecimal.ZERO;
                amount = defaultAmt.multiply(payFactor).setScale(2, RoundingMode.HALF_UP);
            } else if ("PERCENTAGE".equalsIgnoreCase(comp.getCalculationType())) {
                BigDecimal rate = sc.getPercentageRate() != null ? sc.getPercentageRate() : comp.getPercentageRate();
                if (rate == null) rate = BigDecimal.ZERO;
                if ("BASIC".equalsIgnoreCase(comp.getCalculationBase())) {
                    amount = proRatedBaseSalary.multiply(rate).setScale(2, RoundingMode.HALF_UP);
                } else {
                    amount = (activeAssignment.getMonthlyGross() != null ? activeAssignment.getMonthlyGross() : BigDecimal.ZERO)
                            .multiply(payFactor).multiply(rate).setScale(2, RoundingMode.HALF_UP);
                }
            } else {
                amount = BigDecimal.ZERO;
            }

            if ("EARNING".equalsIgnoreCase(comp.getType())) {
                totalGross = totalGross.add(amount);
                PayrollLineItem li = PayrollLineItem.builder()
                        .salaryComponent(comp)
                        .componentCode(comp.getCode())
                        .componentName(comp.getName())
                        .type("EARNING")
                        .amount(amount)
                        .build();
                li.setTenantId(tenantId);
                lineItems.add(li);
            }
        }

        if (totalGross.compareTo(BigDecimal.ZERO) == 0 && activeAssignment.getMonthlyGross() != null) {
            totalGross = activeAssignment.getMonthlyGross().multiply(payFactor).setScale(2, RoundingMode.HALF_UP);
            PayrollLineItem li = PayrollLineItem.builder()
                    .componentCode("GROSS_EARNING").componentName("Gross Salary")
                    .type("EARNING").amount(totalGross).build();
            li.setTenantId(tenantId);
            lineItems.add(li);
        }

        // 10. Statutory: EPF
        // EPF NOTE: For September 2026 transition, the rule applicable to the period END DATE
        // is used for the entire period (month-of-change treatment). See class Javadoc.
        // TODO_LEGAL: Confirm with statutory advisor whether partial-month split is required.
        Optional<StatutoryRule> epfOpt = statutoryRuleRepository.findApplicableRuleByDate(
                tenantId, "EPF", null, endDate);
        if (epfOpt.isPresent()) {
            StatutoryRule epf = epfOpt.get();
            // EPF basis: pro-rated basic salary (not gross)
            BigDecimal epfWage = proRatedBaseSalary;
            if (epf.getWageCeiling() != null && epfWage.compareTo(epf.getWageCeiling()) > 0) {
                epfWage = epf.getWageCeiling();
            }
            BigDecimal epfEmployee = epfWage.multiply(epf.getEmployeeRate()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal epfEmployer = epfWage.multiply(epf.getEmployerRate()).setScale(2, RoundingMode.HALF_UP);
            // EPS, EDLI and admin charges: TODO_LEGAL — pending official formula validation.

            if (epfEmployee.compareTo(BigDecimal.ZERO) > 0) {
                totalDeductions = totalDeductions.add(epfEmployee);
                PayrollLineItem li = PayrollLineItem.builder()
                        .componentCode("PF_EMPLOYEE").componentName("Provident Fund (Employee)")
                        .type("DEDUCTION").amount(epfEmployee).build();
                li.setTenantId(tenantId);
                lineItems.add(li);
            }
            if (epfEmployer.compareTo(BigDecimal.ZERO) > 0) {
                totalEmployerCost = totalEmployerCost.add(epfEmployer);
                PayrollLineItem li = PayrollLineItem.builder()
                        .componentCode("PF_EMPLOYER").componentName("Provident Fund (Employer)")
                        .type("EMPLOYER_CONTRIBUTION").amount(epfEmployer).build();
                li.setTenantId(tenantId);
                lineItems.add(li);
            }
        } else {
            // Fallback: year-based lookup for backward compatibility
            List<StatutoryRule> epfRules = statutoryRuleRepository.findApplicableRules(
                    tenantId, "EPF", null, period.getYear());
            if (!epfRules.isEmpty()) {
                StatutoryRule epf = epfRules.get(0);
                BigDecimal epfWage = proRatedBaseSalary;
                if (epf.getWageCeiling() != null && epfWage.compareTo(epf.getWageCeiling()) > 0) {
                    epfWage = epf.getWageCeiling();
                }
                BigDecimal epfEmployee = epfWage.multiply(epf.getEmployeeRate()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal epfEmployer = epfWage.multiply(epf.getEmployerRate()).setScale(2, RoundingMode.HALF_UP);
                if (epfEmployee.compareTo(BigDecimal.ZERO) > 0) {
                    totalDeductions = totalDeductions.add(epfEmployee);
                    PayrollLineItem li = PayrollLineItem.builder()
                            .componentCode("PF_EMPLOYEE").componentName("Provident Fund (Employee)")
                            .type("DEDUCTION").amount(epfEmployee).build();
                    li.setTenantId(tenantId);
                    lineItems.add(li);
                }
                if (epfEmployer.compareTo(BigDecimal.ZERO) > 0) {
                    totalEmployerCost = totalEmployerCost.add(epfEmployer);
                    PayrollLineItem li = PayrollLineItem.builder()
                            .componentCode("PF_EMPLOYER").componentName("Provident Fund (Employer)")
                            .type("EMPLOYER_CONTRIBUTION").amount(epfEmployer).build();
                    li.setTenantId(tenantId);
                    lineItems.add(li);
                }
            }
        }

        // 11. Statutory: ESI
        // NOTE: ESI disability eligibility (Rs.25,000 ceiling) is NOT applied automatically.
        // It requires legal confirmation of the applicable disability definition and
        // individual employee eligibility determination. Pending professional validation.
        // TODO_LEGAL: Implement disability eligibility after legal sign-off.
        Optional<StatutoryRule> esiOpt = statutoryRuleRepository.findApplicableRuleByDate(
                tenantId, "ESI", null, endDate);
        if (esiOpt.isPresent()) {
            StatutoryRule esi = esiOpt.get();
            if (esi.getWageCeiling() == null || totalGross.compareTo(esi.getWageCeiling()) <= 0) {
                BigDecimal esiEmp = totalGross.multiply(esi.getEmployeeRate()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal esiEmpr = totalGross.multiply(esi.getEmployerRate()).setScale(2, RoundingMode.HALF_UP);
                if (esiEmp.compareTo(BigDecimal.ZERO) > 0) {
                    totalDeductions = totalDeductions.add(esiEmp);
                    PayrollLineItem li = PayrollLineItem.builder()
                            .componentCode("ESI_EMPLOYEE").componentName("Employee State Insurance")
                            .type("DEDUCTION").amount(esiEmp).build();
                    li.setTenantId(tenantId);
                    lineItems.add(li);
                }
                if (esiEmpr.compareTo(BigDecimal.ZERO) > 0) {
                    totalEmployerCost = totalEmployerCost.add(esiEmpr);
                    PayrollLineItem li = PayrollLineItem.builder()
                            .componentCode("ESI_EMPLOYER").componentName("Employer State Insurance")
                            .type("EMPLOYER_CONTRIBUTION").amount(esiEmpr).build();
                    li.setTenantId(tenantId);
                    lineItems.add(li);
                }
            }
        } else {
            List<StatutoryRule> esiRules = statutoryRuleRepository.findApplicableRules(
                    tenantId, "ESI", null, period.getYear());
            if (!esiRules.isEmpty()) {
                StatutoryRule esi = esiRules.get(0);
                if (esi.getWageCeiling() == null || totalGross.compareTo(esi.getWageCeiling()) <= 0) {
                    BigDecimal esiEmp = totalGross.multiply(esi.getEmployeeRate()).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal esiEmpr = totalGross.multiply(esi.getEmployerRate()).setScale(2, RoundingMode.HALF_UP);
                    if (esiEmp.compareTo(BigDecimal.ZERO) > 0) {
                        totalDeductions = totalDeductions.add(esiEmp);
                        PayrollLineItem li = PayrollLineItem.builder()
                                .componentCode("ESI_EMPLOYEE").componentName("Employee State Insurance")
                                .type("DEDUCTION").amount(esiEmp).build();
                        li.setTenantId(tenantId);
                        lineItems.add(li);
                    }
                    if (esiEmpr.compareTo(BigDecimal.ZERO) > 0) {
                        totalEmployerCost = totalEmployerCost.add(esiEmpr);
                        PayrollLineItem li = PayrollLineItem.builder()
                                .componentCode("ESI_EMPLOYER").componentName("Employer State Insurance")
                                .type("EMPLOYER_CONTRIBUTION").amount(esiEmpr).build();
                        li.setTenantId(tenantId);
                        lineItems.add(li);
                    }
                }
            }
        }

        // 12. Professional Tax
        Optional<StatutoryRule> ptOpt = statutoryRuleRepository.findApplicableRuleByDate(
                tenantId, "PROFESSIONAL_TAX", null, endDate);
        List<StatutoryRule> ptList = ptOpt.map(Collections::singletonList)
                .orElseGet(() -> statutoryRuleRepository.findApplicableRules(
                        tenantId, "PROFESSIONAL_TAX", null, period.getYear()));
        if (!ptList.isEmpty()) {
            StatutoryRule pt = ptList.get(0);
            if (totalGross.compareTo(pt.getMinimumWageThreshold()) >= 0) {
                BigDecimal ptAmt = pt.getEmployeeRate();
                totalDeductions = totalDeductions.add(ptAmt);
                PayrollLineItem li = PayrollLineItem.builder()
                        .componentCode("PROFESSIONAL_TAX").componentName("Professional Tax")
                        .type("DEDUCTION").amount(ptAmt).build();
                li.setTenantId(tenantId);
                lineItems.add(li);
            }
        }

        // 13. Adjustments
        List<PayrollAdjustment> adjustments = adjustmentRepository
                .findByTenantIdAndEmployeeIdAndIsProcessedFalse(tenantId, employee.getId());
        for (PayrollAdjustment adj : adjustments) {
            BigDecimal amt = adj.getAmount();
            if (amt.compareTo(BigDecimal.ZERO) > 0) {
                totalGross = totalGross.add(amt);
                PayrollLineItem li = PayrollLineItem.builder()
                        .componentCode("ADJ_" + adj.getAdjustmentType())
                        .componentName("Adjustment: " + adj.getAdjustmentType())
                        .type("EARNING").amount(amt).build();
                li.setTenantId(tenantId);
                lineItems.add(li);
            } else {
                BigDecimal posAmt = amt.abs();
                totalDeductions = totalDeductions.add(posAmt);
                PayrollLineItem li = PayrollLineItem.builder()
                        .componentCode("ADJ_" + adj.getAdjustmentType())
                        .componentName("Adjustment: " + adj.getAdjustmentType())
                        .type("DEDUCTION").amount(posAmt).build();
                li.setTenantId(tenantId);
                lineItems.add(li);
            }
        }

        // 14. Net salary (deductions are applied AFTER payable-days reduction — no double-deduction risk)
        BigDecimal netSalary = totalGross.subtract(totalDeductions);
        if (netSalary.compareTo(BigDecimal.ZERO) < 0) netSalary = BigDecimal.ZERO;

        // 15. Snapshot
        Map<String, Object> snapshotData = new LinkedHashMap<>();
        snapshotData.put("employeeId", employee.getId().toString());
        snapshotData.put("employeeCode", employee.getEmployeeCode());
        snapshotData.put("salaryStructureId", structure.getId().toString());
        snapshotData.put("annualCtc", activeAssignment.getAnnualCtc());
        snapshotData.put("prorationPolicy", prorationPolicy);
        snapshotData.put("totalDaysInMonth", totalDays);
        snapshotData.put("totalApplicableDays", totalApplicableDays);
        snapshotData.put("payableActiveDays", payableActiveDays);
        snapshotData.put("unpaidLeaveDays", unpaidLeaveDays);
        snapshotData.put("payableDays", payableDays);
        snapshotData.put("payFactor", payFactor);
        snapshotData.put("grossEarnings", totalGross);
        snapshotData.put("totalDeductions", totalDeductions);
        snapshotData.put("netSalary", netSalary);
        snapshotData.put("calculatedAt", java.time.Instant.now().toString());

        String snapshotJson = "{}";
        try { snapshotJson = objectMapper.writeValueAsString(snapshotData); }
        catch (Exception e) { log.warn("Failed to serialize payroll snapshot: {}", e.getMessage()); }

        return CalculationResult.builder()
                .totalDaysInMonth(totalDays)
                .payableDays(payableDays)
                .unpaidLeaveDays(unpaidLeaveDays)
                .workedDays(payableDays)
                .holidaysAndWeeklyOffs(BigDecimal.ZERO)
                .grossEarnings(totalGross)
                .totalDeductions(totalDeductions)
                .employerContributions(totalEmployerCost)
                .netSalary(netSalary)
                .lineItems(lineItems)
                .snapshotJson(snapshotJson)
                .build();
    }

    // --- Private helpers ---

    private String resolveProrationPolicy(UUID tenantId) {
        try {
            return payrollSettingRepository.findByTenantId(tenantId)
                    .map(PayrollSetting::getProrationPolicy)
                    .orElse("CALENDAR_DAYS");
        } catch (Exception e) {
            log.warn("Failed to fetch payroll setting, defaulting to CALENDAR_DAYS: {}", e.getMessage());
            return "CALENDAR_DAYS";
        }
    }

    /**
     * Build the set of holiday dates for the employee's branch in the given date range.
     * Includes both nationwide (branch=null) and branch-specific holidays.
     */
    private Set<LocalDate> buildHolidaySet(UUID tenantId, Employee employee, LocalDate start, LocalDate end) {
        UUID branchId = (employee.getBranch() != null) ? employee.getBranch().getId() : null;
        if (branchId != null) {
            return holidayRepository.findHolidaysForBranchAndDateRange(tenantId, branchId, start, end)
                    .stream().map(Holiday::getHolidayDate).collect(Collectors.toSet());
        }
        return holidayRepository.findByTenantIdAndIsActiveTrueOrderByHolidayDateAsc(tenantId)
                .stream()
                .filter(h -> !h.getHolidayDate().isBefore(start) && !h.getHolidayDate().isAfter(end))
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toSet());
    }

    /**
     * Determine the employee's weekly-off days from their shift roster.
     * Falls back to Saturday+Sunday if no roster is found.
     */
    private Set<DayOfWeek> resolveWeeklyOffDays(UUID tenantId, Employee employee, LocalDate start, LocalDate end) {
        try {
            List<ShiftRoster> rosters = shiftRosterRepository
                    .findByTenantIdAndEmployeeIdAndRosterDateBetweenOrderByRosterDateAsc(tenantId, employee.getId(), start, end);
            if (rosters != null && !rosters.isEmpty()) {
                Set<DayOfWeek> offDays = new HashSet<>();
                for (ShiftRoster r : rosters) {
                    if (Boolean.TRUE.equals(r.getIsWeeklyOff())) {
                        offDays.add(r.getRosterDate().getDayOfWeek());
                    }
                }
                if (!offDays.isEmpty()) return offDays;
            }
        } catch (Exception e) {
            log.warn("Could not resolve shift roster for employee {}: {}", employee.getEmployeeCode(), e.getMessage());
        }
        // Default weekly offs
        return Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
    }

    private int computePayableActiveDays(String policy, LocalDate empActiveStart, LocalDate empActiveEnd,
                                         int totalDays, Set<LocalDate> holidayDates, Set<DayOfWeek> weeklyOffDays) {
        if (empActiveStart.isAfter(empActiveEnd)) return 0;
        if ("WORKING_DAYS".equalsIgnoreCase(policy)) {
            int working = 0;
            for (LocalDate d = empActiveStart; !d.isAfter(empActiveEnd); d = d.plusDays(1)) {
                if (!weeklyOffDays.contains(d.getDayOfWeek()) && !holidayDates.contains(d)) {
                    working++;
                }
            }
            return Math.min(working, totalDays);
        }
        // CALENDAR_DAYS
        int days = (int) java.time.temporal.ChronoUnit.DAYS.between(empActiveStart, empActiveEnd) + 1;
        return Math.min(Math.max(days, 0), totalDays);
    }

    private int computeWorkingDaysInPeriod(LocalDate start, LocalDate end,
                                           Set<LocalDate> holidayDates, Set<DayOfWeek> weeklyOffDays) {
        if (start.isAfter(end)) return 0;
        int working = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (!weeklyOffDays.contains(d.getDayOfWeek()) && !holidayDates.contains(d)) {
                working++;
            }
        }
        return working;
    }

    private BigDecimal computeUnpaidLeaveDays(UUID tenantId, Employee employee, LocalDate start, LocalDate end) {
        BigDecimal unpaid = BigDecimal.ZERO;
        List<LeaveApplication> apps = leaveApplicationRepository
                .findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(tenantId, employee.getId());
        for (LeaveApplication app : apps) {
            if ("APPROVED".equals(app.getStatus()) && !app.getLeaveType().getIsPaid()) {
                LocalDate lStart = app.getStartDate().isBefore(start) ? start : app.getStartDate();
                LocalDate lEnd = app.getEndDate().isAfter(end) ? end : app.getEndDate();
                if (!lStart.isAfter(lEnd)) {
                    long days = java.time.temporal.ChronoUnit.DAYS.between(lStart, lEnd) + 1;
                    unpaid = unpaid.add(Boolean.TRUE.equals(app.getIsHalfDay())
                            ? BigDecimal.valueOf(0.5) : BigDecimal.valueOf(days));
                }
            }
        }
        return unpaid;
    }
}



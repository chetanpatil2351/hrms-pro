package com.hrmspro.modules.payroll;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrmspro.modules.attendance.AttendanceRecord;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class PayrollCalculationEngine {

    private final EmployeeSalaryStructureRepository employeeSalaryStructureRepository;
    private final SalaryStructureComponentRepository structureComponentRepository;
    private final StatutoryRuleRepository statutoryRuleRepository;
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

        // 1. Determine active salary assignment for this employee as of period end date (or start date)
        List<EmployeeSalaryStructure> assignments = employeeSalaryStructureRepository.findActiveAssignmentsAsOf(
                tenantId, employee.getId(), endDate
        );
        if (assignments.isEmpty()) {
            // Check if there was an assignment as of startDate
            assignments = employeeSalaryStructureRepository.findActiveAssignmentsAsOf(
                    tenantId, employee.getId(), startDate
            );
        }
        if (assignments.isEmpty()) {
            throw new IllegalStateException("No salary structure assignment found for employee: " + employee.getEmployeeCode());
        }
        EmployeeSalaryStructure activeAssignment = assignments.get(0);
        SalaryStructure structure = activeAssignment.getSalaryStructure();

        // 2. Calculate mid-month joining / exit active range
        LocalDate empActiveStart = employee.getJoiningDate().isAfter(startDate) ? employee.getJoiningDate() : startDate;
        LocalDate empActiveEnd = endDate; // unless terminated before endDate
        // Eligible working calendar days in period
        int calendarActiveDays = 0;
        if (!empActiveStart.isAfter(empActiveEnd)) {
            calendarActiveDays = (int) (java.time.temporal.ChronoUnit.DAYS.between(empActiveStart, empActiveEnd) + 1);
        }
        if (calendarActiveDays < 0) calendarActiveDays = 0;
        if (calendarActiveDays > totalDays) calendarActiveDays = totalDays;

        // 3. Count Unpaid Leave (Loss of Pay) in the period
        // Check leave applications overlapping period
        BigDecimal unpaidLeaveDays = BigDecimal.ZERO;
        List<LeaveApplication> leaveApplications = leaveApplicationRepository.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(tenantId, employee.getId());
        for (LeaveApplication app : leaveApplications) {
            if ("APPROVED".equals(app.getStatus()) && !app.getLeaveType().getIsPaid()) {
                // compute overlap with period
                LocalDate lStart = app.getStartDate().isBefore(startDate) ? startDate : app.getStartDate();
                LocalDate lEnd = app.getEndDate().isAfter(endDate) ? endDate : app.getEndDate();
                if (!lStart.isAfter(lEnd)) {
                    long days = java.time.temporal.ChronoUnit.DAYS.between(lStart, lEnd) + 1;
                    if (Boolean.TRUE.equals(app.getIsHalfDay())) {
                        unpaidLeaveDays = unpaidLeaveDays.add(BigDecimal.valueOf(0.50));
                    } else {
                        unpaidLeaveDays = unpaidLeaveDays.add(BigDecimal.valueOf(days));
                    }
                }
            }
        }

        // 4. Payable Days calculation
        BigDecimal payableDays = BigDecimal.valueOf(calendarActiveDays).subtract(unpaidLeaveDays);
        if (payableDays.compareTo(BigDecimal.ZERO) < 0) {
            payableDays = BigDecimal.ZERO;
        }

        // Factor of pay: payableDays / totalDays
        BigDecimal payFactor = payableDays.divide(BigDecimal.valueOf(totalDays), 6, RoundingMode.HALF_UP);

        // 5. Calculate Earnings & Employer Contributions from structure components
        List<SalaryStructureComponent> structureComponents = structureComponentRepository
                .findBySalaryStructureIdOrderByDisplayOrderAsc(structure.getId());

        List<PayrollLineItem> lineItems = new ArrayList<>();
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalDeductions = BigDecimal.ZERO;
        BigDecimal totalEmployerCost = BigDecimal.ZERO;

        BigDecimal baseMonthlySalary = activeAssignment.getMonthlyBaseSalary();
        BigDecimal proRatedBaseSalary = baseMonthlySalary.multiply(payFactor).setScale(2, RoundingMode.HALF_UP);

        // First pass: Calculate Basic and Fixed earnings
        Map<String, BigDecimal> computedAmounts = new HashMap<>();
        computedAmounts.put("BASIC", proRatedBaseSalary);

        for (SalaryStructureComponent sc : structureComponents) {
            SalaryComponent comp = sc.getSalaryComponent();
            BigDecimal amount = BigDecimal.ZERO;

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
                    amount = activeAssignment.getMonthlyGross().multiply(payFactor).multiply(rate).setScale(2, RoundingMode.HALF_UP);
                }
            }

            computedAmounts.put(comp.getCode().toUpperCase(), amount);

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

        // If totalGross is 0 (e.g., custom components only with no basic mapped), fallback to pro-rated gross
        if (totalGross.compareTo(BigDecimal.ZERO) == 0 && activeAssignment.getMonthlyGross() != null) {
            totalGross = activeAssignment.getMonthlyGross().multiply(payFactor).setScale(2, RoundingMode.HALF_UP);
            PayrollLineItem li = PayrollLineItem.builder()
                    .componentCode("GROSS_EARNING")
                    .componentName("Gross Salary")
                    .type("EARNING")
                    .amount(totalGross)
                    .build();
            li.setTenantId(tenantId);
            lineItems.add(li);
        }

        // 6. Statutory Rules Application (EPF, ESI, Professional Tax)
        // EPF: 12% on Basic (capped at wage ceiling, default 15000 if wage ceiling applied, or actual Basic)
        List<StatutoryRule> epfRules = statutoryRuleRepository.findApplicableRules(tenantId, "EPF", null, period.getYear());
        if (!epfRules.isEmpty()) {
            StatutoryRule epf = epfRules.get(0);
            BigDecimal epfWage = proRatedBaseSalary;
            if (epf.getWageCeiling() != null && epfWage.compareTo(epf.getWageCeiling()) > 0) {
                // If wage ceiling applies, cap to ceiling
                epfWage = epf.getWageCeiling();
            }
            BigDecimal epfEmployee = epfWage.multiply(epf.getEmployeeRate()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal epfEmployer = epfWage.multiply(epf.getEmployerRate()).setScale(2, RoundingMode.HALF_UP);

            if (epfEmployee.compareTo(BigDecimal.ZERO) > 0) {
                totalDeductions = totalDeductions.add(epfEmployee);
                PayrollLineItem li = PayrollLineItem.builder()
                        .componentCode("PF_EMPLOYEE")
                        .componentName("Provident Fund (Employee)")
                        .type("DEDUCTION")
                        .amount(epfEmployee)
                        .build();
                li.setTenantId(tenantId);
                lineItems.add(li);
            }
            if (epfEmployer.compareTo(BigDecimal.ZERO) > 0) {
                totalEmployerCost = totalEmployerCost.add(epfEmployer);
                PayrollLineItem li = PayrollLineItem.builder()
                        .componentCode("PF_EMPLOYER")
                        .componentName("Provident Fund (Employer)")
                        .type("EMPLOYER_CONTRIBUTION")
                        .amount(epfEmployer)
                        .build();
                li.setTenantId(tenantId);
                lineItems.add(li);
            }
        }

        // ESI: 0.75% employee, 3.25% employer if Gross <= 21,000 ceiling
        List<StatutoryRule> esiRules = statutoryRuleRepository.findApplicableRules(tenantId, "ESI", null, period.getYear());
        if (!esiRules.isEmpty()) {
            StatutoryRule esi = esiRules.get(0);
            if (esi.getWageCeiling() == null || totalGross.compareTo(esi.getWageCeiling()) <= 0) {
                BigDecimal esiEmp = totalGross.multiply(esi.getEmployeeRate()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal esiEmpr = totalGross.multiply(esi.getEmployerRate()).setScale(2, RoundingMode.HALF_UP);

                if (esiEmp.compareTo(BigDecimal.ZERO) > 0) {
                    totalDeductions = totalDeductions.add(esiEmp);
                    PayrollLineItem li = PayrollLineItem.builder()
                            .componentCode("ESI_EMPLOYEE")
                            .componentName("Employee State Insurance")
                            .type("DEDUCTION")
                            .amount(esiEmp)
                            .build();
                    li.setTenantId(tenantId);
                    lineItems.add(li);
                }
                if (esiEmpr.compareTo(BigDecimal.ZERO) > 0) {
                    totalEmployerCost = totalEmployerCost.add(esiEmpr);
                    PayrollLineItem li = PayrollLineItem.builder()
                            .componentCode("ESI_EMPLOYER")
                            .componentName("Employer State Insurance")
                            .type("EMPLOYER_CONTRIBUTION")
                            .amount(esiEmpr)
                            .build();
                    li.setTenantId(tenantId);
                    lineItems.add(li);
                }
            }
        }

        // Professional Tax (PT): standard slab (e.g. 200 INR if gross > 10000)
        List<StatutoryRule> ptRules = statutoryRuleRepository.findApplicableRules(tenantId, "PROFESSIONAL_TAX", null, period.getYear());
        if (!ptRules.isEmpty()) {
            StatutoryRule pt = ptRules.get(0);
            if (totalGross.compareTo(pt.getMinimumWageThreshold()) >= 0) {
                BigDecimal ptAmt = pt.getEmployeeRate(); // default fixed amount if configured as rate
                totalDeductions = totalDeductions.add(ptAmt);
                PayrollLineItem li = PayrollLineItem.builder()
                        .componentCode("PROFESSIONAL_TAX")
                        .componentName("Professional Tax")
                        .type("DEDUCTION")
                        .amount(ptAmt)
                        .build();
                li.setTenantId(tenantId);
                lineItems.add(li);
            }
        }

        // 7. Adjustments (Advances, Reimbursements, Bonuses)
        List<PayrollAdjustment> adjustments = adjustmentRepository.findByTenantIdAndEmployeeIdAndIsProcessedFalse(tenantId, employee.getId());
        for (PayrollAdjustment adj : adjustments) {
            BigDecimal amt = adj.getAmount();
            if (amt.compareTo(BigDecimal.ZERO) > 0) {
                // Earning / reimbursement / bonus
                totalGross = totalGross.add(amt);
                PayrollLineItem li = PayrollLineItem.builder()
                        .componentCode("ADJ_" + adj.getAdjustmentType())
                        .componentName("Adjustment: " + adj.getAdjustmentType())
                        .type("EARNING")
                        .amount(amt)
                        .build();
                li.setTenantId(tenantId);
                lineItems.add(li);
            } else {
                // Deduction / advance recovery
                BigDecimal posAmt = amt.abs();
                totalDeductions = totalDeductions.add(posAmt);
                PayrollLineItem li = PayrollLineItem.builder()
                        .componentCode("ADJ_" + adj.getAdjustmentType())
                        .componentName("Adjustment: " + adj.getAdjustmentType())
                        .type("DEDUCTION")
                        .amount(posAmt)
                        .build();
                li.setTenantId(tenantId);
                lineItems.add(li);
            }
        }

        // 8. Net Salary
        BigDecimal netSalary = totalGross.subtract(totalDeductions);
        if (netSalary.compareTo(BigDecimal.ZERO) < 0) {
            netSalary = BigDecimal.ZERO;
        }

        // 9. Snapshot construction
        Map<String, Object> snapshotData = new HashMap<>();
        snapshotData.put("employeeId", employee.getId().toString());
        snapshotData.put("employeeCode", employee.getEmployeeCode());
        snapshotData.put("salaryStructureId", structure.getId().toString());
        snapshotData.put("annualCtc", activeAssignment.getAnnualCtc());
        snapshotData.put("calendarActiveDays", calendarActiveDays);
        snapshotData.put("totalDaysInMonth", totalDays);
        snapshotData.put("payableDays", payableDays);
        snapshotData.put("unpaidLeaveDays", unpaidLeaveDays);
        snapshotData.put("grossEarnings", totalGross);
        snapshotData.put("totalDeductions", totalDeductions);
        snapshotData.put("netSalary", netSalary);
        snapshotData.put("calculatedAt", java.time.Instant.now().toString());

        String snapshotJson = "{}";
        try {
            snapshotJson = objectMapper.writeValueAsString(snapshotData);
        } catch (Exception e) {
            log.warn("Failed to serialize payroll calculation snapshot: {}", e.getMessage());
        }

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
}

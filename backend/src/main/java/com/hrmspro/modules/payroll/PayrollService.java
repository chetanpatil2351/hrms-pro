package com.hrmspro.modules.payroll;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrmspro.common.context.TenantContext;
import com.hrmspro.common.exception.ResourceNotFoundException;
import com.hrmspro.modules.employee.Employee;
import com.hrmspro.modules.employee.EmployeeRepository;
import com.hrmspro.modules.payroll.dto.*;
import com.hrmspro.modules.user.AppUser;
import com.hrmspro.modules.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollService {

    private final PayrollPeriodRepository periodRepository;
    private final PayrollRunRepository runRepository;
    private final PayrollRunEmployeeRepository runEmployeeRepository;
    private final PayrollLineItemRepository lineItemRepository;
    private final PayrollCalculationSnapshotRepository snapshotRepository;
    private final PayrollAdjustmentRepository adjustmentRepository;
    private final PayslipRepository payslipRepository;
    private final EmployeeRepository employeeRepository;
    private final AppUserRepository appUserRepository;
    private final PayrollCalculationEngine calculationEngine;
    private final StatutoryRuleRepository statutoryRuleRepository;
    private final ObjectMapper objectMapper;

    private UUID getRequiredTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context not set");
        }
        return tenantId;
    }

    // --- Statutory Rules Management ---

    @Transactional
    public StatutoryRuleDto configureStatutoryRule(StatutoryRuleDto dto) {
        UUID tenantId = getRequiredTenantId();
        StatutoryRule rule = StatutoryRule.builder()
                .ruleType(dto.getRuleType().toUpperCase())
                .state(dto.getState() != null ? dto.getState().toUpperCase() : null)
                .effectiveYear(dto.getEffectiveYear())
                .employeeRate(dto.getEmployeeRate() != null ? dto.getEmployeeRate() : BigDecimal.ZERO)
                .employerRate(dto.getEmployerRate() != null ? dto.getEmployerRate() : BigDecimal.ZERO)
                .wageCeiling(dto.getWageCeiling())
                .minimumWageThreshold(dto.getMinimumWageThreshold() != null ? dto.getMinimumWageThreshold() : BigDecimal.ZERO)
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .effectiveFrom(dto.getEffectiveFrom() != null ? dto.getEffectiveFrom() : LocalDate.of(1970, 1, 1))
                .effectiveTo(dto.getEffectiveTo())
                .jurisdiction(dto.getJurisdiction() != null ? dto.getJurisdiction() : "ALL")
                .sourceReference(dto.getSourceReference())
                .ruleVersion(dto.getRuleVersion())
                .disabilityCeiling(dto.getDisabilityCeiling())
                .build();
        rule.setTenantId(tenantId);
        rule = statutoryRuleRepository.save(rule);

        return toStatutoryRuleDto(rule);
    }

    public List<StatutoryRuleDto> getStatutoryRules(Integer year) {
        UUID tenantId = getRequiredTenantId();
        int targetYear = year != null ? year : LocalDate.now().getYear();
        return statutoryRuleRepository.findByTenantIdAndEffectiveYear(tenantId, targetYear)
                .stream()
                .map(this::toStatutoryRuleDto)
                .collect(Collectors.toList());
    }

    // --- Period Management ---

    @Transactional
    public PayrollPeriodDto createPeriod(PayrollPeriodDto dto) {
        UUID tenantId = getRequiredTenantId();
        if (periodRepository.existsByTenantIdAndYearAndMonth(tenantId, dto.getYear(), dto.getMonth())) {
            throw new IllegalArgumentException("Payroll period for " + dto.getYear() + "-" + dto.getMonth() + " already exists");
        }

        YearMonth ym = YearMonth.of(dto.getYear(), dto.getMonth());
        LocalDate startDate = ym.atDay(1);
        LocalDate endDate = ym.atEndOfMonth();

        PayrollPeriod period = PayrollPeriod.builder()
                .year(dto.getYear())
                .month(dto.getMonth())
                .startDate(startDate)
                .endDate(endDate)
                .status("DRAFT")
                .totalEmployees(0)
                .totalGrossPay(BigDecimal.ZERO)
                .totalNetPay(BigDecimal.ZERO)
                .totalEmployerCost(BigDecimal.ZERO)
                .build();
        period.setTenantId(tenantId);
        period = periodRepository.save(period);

        return toPeriodDto(period);
    }

    public Page<PayrollPeriodDto> getPeriods(Pageable pageable) {
        UUID tenantId = getRequiredTenantId();
        return periodRepository.findByTenantIdOrderByYearDescMonthDesc(tenantId, pageable)
                .map(this::toPeriodDto);
    }

    public PayrollPeriodDto getPeriod(UUID periodId) {
        UUID tenantId = getRequiredTenantId();
        PayrollPeriod period = periodRepository.findByIdAndTenantId(periodId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll period not found: " + periodId));
        return toPeriodDto(period);
    }

    // --- Payroll Run Execution ---

    @Transactional
    public PayrollRunResultDto calculatePayrollRun(UUID periodId, UUID currentUserId) {
        UUID tenantId = getRequiredTenantId();
        PayrollPeriod period = periodRepository.findByIdAndTenantId(periodId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll period not found: " + periodId));

        if ("FINALIZED".equals(period.getStatus()) || "PAID".equals(period.getStatus())) {
            throw new IllegalStateException("Cannot re-run payroll on a period with status: " + period.getStatus());
        }

        period.setStatus("PROCESSING");
        periodRepository.save(period);

        AppUser initiator = currentUserId != null ? appUserRepository.findById(currentUserId).orElse(null) : null;
        int nextRunNumber = runRepository.findMaxRunNumber(tenantId, period.getId()) + 1;

        PayrollRun run = PayrollRun.builder()
                .payrollPeriod(period)
                .runType("REGULAR")
                .runNumber(nextRunNumber)
                .status("PROCESSING")
                .initiatedBy(initiator)
                .build();
        run.setTenantId(tenantId);
        run = runRepository.save(run);

        // Fetch eligible active employees for this tenant
        List<Employee> employees = employeeRepository.findAll().stream()
                .filter(e -> tenantId.equals(e.getTenantId()))
                .filter(e -> "ACTIVE".equalsIgnoreCase(e.getEmploymentStatus()) || "PROBATION".equalsIgnoreCase(e.getEmploymentStatus()))
                .filter(e -> !e.getJoiningDate().isAfter(period.getEndDate()))
                .collect(Collectors.toList());

        BigDecimal runTotalGross = BigDecimal.ZERO;
        BigDecimal runTotalDeductions = BigDecimal.ZERO;
        BigDecimal runTotalNet = BigDecimal.ZERO;
        BigDecimal runTotalEmployerCost = BigDecimal.ZERO;

        List<PayrollRunResultDto.EmployeePayrollSummaryDto> summaries = new ArrayList<>();

        for (Employee emp : employees) {
            try {
                PayrollCalculationEngine.CalculationResult calc = calculationEngine.calculateEmployeePayroll(tenantId, emp, period);

                PayrollRunEmployee runEmp = PayrollRunEmployee.builder()
                        .payrollRun(run)
                        .employee(emp)
                        .totalDays(calc.getTotalDaysInMonth())
                        .payableDays(calc.getPayableDays())
                        .unpaidLeaveDays(calc.getUnpaidLeaveDays())
                        .workedDays(calc.getWorkedDays())
                        .holidaysAndWeeklyOffs(calc.getHolidaysAndWeeklyOffs())
                        .grossEarnings(calc.getGrossEarnings())
                        .totalDeductions(calc.getTotalDeductions())
                        .employerContributions(calc.getEmployerContributions())
                        .netSalary(calc.getNetSalary())
                        .isFrozen(false)
                        .build();
                runEmp.setTenantId(tenantId);
                runEmp = runEmployeeRepository.save(runEmp);

                for (PayrollLineItem li : calc.getLineItems()) {
                    li.setPayrollRunEmployee(runEmp);
                    lineItemRepository.save(li);
                }

                // Save JSON snapshot
                PayrollCalculationSnapshot snapshot = PayrollCalculationSnapshot.builder()
                        .payrollRunEmployee(runEmp)
                        .snapshotJson(calc.getSnapshotJson())
                        .build();
                snapshot.setTenantId(tenantId);
                snapshotRepository.save(snapshot);

                runTotalGross = runTotalGross.add(calc.getGrossEarnings());
                runTotalDeductions = runTotalDeductions.add(calc.getTotalDeductions());
                runTotalNet = runTotalNet.add(calc.getNetSalary());
                runTotalEmployerCost = runTotalEmployerCost.add(calc.getEmployerContributions());

                List<PayrollRunResultDto.LineItemDto> lineItemDtos = calc.getLineItems().stream()
                        .map(li -> PayrollRunResultDto.LineItemDto.builder()
                                .componentCode(li.getComponentCode())
                                .componentName(li.getComponentName())
                                .type(li.getType())
                                .amount(li.getAmount())
                                .build())
                        .collect(Collectors.toList());

                summaries.add(PayrollRunResultDto.EmployeePayrollSummaryDto.builder()
                        .employeeId(emp.getId())
                        .employeeCode(emp.getEmployeeCode())
                        .employeeName(emp.getFirstName() + " " + emp.getLastName())
                        .totalDays(calc.getTotalDaysInMonth())
                        .payableDays(calc.getPayableDays())
                        .unpaidLeaveDays(calc.getUnpaidLeaveDays())
                        .grossEarnings(calc.getGrossEarnings())
                        .totalDeductions(calc.getTotalDeductions())
                        .employerContributions(calc.getEmployerContributions())
                        .netSalary(calc.getNetSalary())
                        .lineItems(lineItemDtos)
                        .build());
            } catch (Exception ex) {
                log.warn("Skipping payroll calculation for employee {}: {}", emp.getEmployeeCode(), ex.getMessage());
            }
        }

        run.setStatus("COMPLETED");
        run.setCompletedAt(Instant.now());
        runRepository.save(run);

        period.setStatus("REVIEW");
        period.setTotalEmployees(summaries.size());
        period.setTotalGrossPay(runTotalGross);
        period.setTotalNetPay(runTotalNet);
        period.setTotalEmployerCost(runTotalEmployerCost);
        periodRepository.save(period);

        return PayrollRunResultDto.builder()
                .runId(run.getId())
                .periodId(period.getId())
                .runNumber(run.getRunNumber())
                .runType(run.getRunType())
                .status(run.getStatus())
                .processedEmployees(summaries.size())
                .totalGross(runTotalGross)
                .totalDeductions(runTotalDeductions)
                .totalNet(runTotalNet)
                .employeeSummaries(summaries)
                .build();
    }

    // --- Approval & Finalization ---

    @Transactional
    public PayrollPeriodDto approvePeriod(UUID periodId) {
        UUID tenantId = getRequiredTenantId();
        PayrollPeriod period = periodRepository.findByIdAndTenantId(periodId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll period not found: " + periodId));

        if (!"REVIEW".equals(period.getStatus())) {
            throw new IllegalStateException("Period can only be approved from REVIEW status. Current: " + period.getStatus());
        }

        period.setStatus("APPROVED");
        period = periodRepository.save(period);
        return toPeriodDto(period);
    }

    @Transactional
    public PayrollPeriodDto finalizePeriod(UUID periodId, UUID currentUserId) {
        UUID tenantId = getRequiredTenantId();
        PayrollPeriod period = periodRepository.findByIdAndTenantId(periodId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll period not found: " + periodId));

        if (!"APPROVED".equals(period.getStatus())) {
            throw new IllegalStateException("Period must be APPROVED before finalization. Current: " + period.getStatus());
        }

        AppUser finalizer = currentUserId != null ? appUserRepository.findById(currentUserId).orElse(null) : null;
        period.setStatus("FINALIZED");
        period.setFinalizedBy(finalizer);
        period.setFinalizedAt(Instant.now());
        period = periodRepository.save(period);

        // Freeze all run employees and generate payslips
        List<PayrollRun> runs = runRepository.findByPayrollPeriodIdOrderByRunNumberDesc(period.getId());
        if (!runs.isEmpty()) {
            PayrollRun latestRun = runs.get(0);
            List<PayrollRunEmployee> runEmps = runEmployeeRepository.findByPayrollRunId(latestRun.getId());

            for (PayrollRunEmployee re : runEmps) {
                re.setIsFrozen(true);
                runEmployeeRepository.save(re);

                // Check if payslip already generated
                if (payslipRepository.findByPayrollPeriodIdAndEmployeeId(period.getId(), re.getEmployee().getId()).isEmpty()) {
                    String payslipNumber = String.format("PS-%d-%02d-%s", period.getYear(), period.getMonth(), re.getEmployee().getEmployeeCode());

                    // Build JSON details
                    Map<String, Object> details = new HashMap<>();
                    details.put("employeeName", re.getEmployee().getFirstName() + " " + re.getEmployee().getLastName());
                    details.put("employeeCode", re.getEmployee().getEmployeeCode());
                    details.put("period", period.getYear() + "-" + period.getMonth());
                    details.put("payableDays", re.getPayableDays());
                    details.put("gross", re.getGrossEarnings());
                    details.put("deductions", re.getTotalDeductions());
                    details.put("net", re.getNetSalary());

                    String json = "{}";
                    try {
                        json = objectMapper.writeValueAsString(details);
                    } catch (Exception ignored) {}

                    Payslip payslip = Payslip.builder()
                            .payrollRunEmployee(re)
                            .employee(re.getEmployee())
                            .payrollPeriod(period)
                            .payslipNumber(payslipNumber)
                            .issueDate(LocalDate.now())
                            .grossSalary(re.getGrossEarnings())
                            .totalDeductions(re.getTotalDeductions())
                            .netSalary(re.getNetSalary())
                            .paymentStatus("UNPAID")
                            .payslipDataJson(json)
                            .build();
                    payslip.setTenantId(tenantId);
                    payslipRepository.save(payslip);
                }
            }
        }

        // Mark associated adjustments as processed
        List<PayrollAdjustment> adjustments = adjustmentRepository.findByPayrollPeriodId(period.getId());
        for (PayrollAdjustment adj : adjustments) {
            adj.setIsProcessed(true);
            adjustmentRepository.save(adj);
        }

        return toPeriodDto(period);
    }

    // --- Adjustments ---

    @Transactional
    public PayrollAdjustmentDto addAdjustment(PayrollAdjustmentDto dto, UUID currentUserId) {
        UUID tenantId = getRequiredTenantId();
        Employee employee = employeeRepository.findByIdAndTenantId(dto.getEmployeeId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + dto.getEmployeeId()));

        if (dto.getIdempotencyKey() != null && adjustmentRepository.existsByTenantIdAndIdempotencyKey(tenantId, dto.getIdempotencyKey())) {
            throw new IllegalArgumentException("Adjustment with idempotency key " + dto.getIdempotencyKey() + " already recorded");
        }

        PayrollPeriod period = null;
        if (dto.getPayrollPeriodId() != null) {
            period = periodRepository.findByIdAndTenantId(dto.getPayrollPeriodId(), tenantId).orElse(null);
        }

        AppUser authorizedBy = currentUserId != null ? appUserRepository.findById(currentUserId).orElse(null) : null;

        PayrollAdjustment adjustment = PayrollAdjustment.builder()
                .employee(employee)
                .payrollPeriod(period)
                .adjustmentType(dto.getAdjustmentType().toUpperCase())
                .amount(dto.getAmount())
                .reason(dto.getReason())
                .isProcessed(false)
                .idempotencyKey(dto.getIdempotencyKey())
                .authorizedBy(authorizedBy)
                .build();
        adjustment.setTenantId(tenantId);
        adjustment = adjustmentRepository.save(adjustment);

        return toAdjustmentDto(adjustment);
    }

    // --- Payslips ---

    public List<PayslipDto> getEmployeePayslips(UUID employeeId) {
        UUID tenantId = getRequiredTenantId();
        if (employeeRepository.findByIdAndTenantId(employeeId, tenantId).isEmpty()) {
            throw new ResourceNotFoundException("Employee not found for current tenant: " + employeeId);
        }
        return payslipRepository.findByTenantIdAndEmployeeIdOrderByIssueDateDesc(tenantId, employeeId)
                .stream()
                .map(this::toPayslipDto)
                .collect(Collectors.toList());
    }

    public PayslipDto getPayslipById(UUID payslipId) {
        UUID tenantId = getRequiredTenantId();
        Payslip payslip = payslipRepository.findByIdAndTenantId(payslipId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payslip not found: " + payslipId));
        return toPayslipDto(payslip);
    }

    // --- Helper Mappers ---

    private StatutoryRuleDto toStatutoryRuleDto(StatutoryRule r) {
        return StatutoryRuleDto.builder()
                .id(r.getId())
                .ruleType(r.getRuleType())
                .state(r.getState())
                .effectiveYear(r.getEffectiveYear())
                .employeeRate(r.getEmployeeRate())
                .employerRate(r.getEmployerRate())
                .wageCeiling(r.getWageCeiling())
                .minimumWageThreshold(r.getMinimumWageThreshold())
                .isActive(r.getIsActive())
                .effectiveFrom(r.getEffectiveFrom())
                .effectiveTo(r.getEffectiveTo())
                .jurisdiction(r.getJurisdiction())
                .sourceReference(r.getSourceReference())
                .ruleVersion(r.getRuleVersion())
                .disabilityCeiling(r.getDisabilityCeiling())
                .build();
    }

    private PayrollPeriodDto toPeriodDto(PayrollPeriod p) {
        return PayrollPeriodDto.builder()
                .id(p.getId())
                .year(p.getYear())
                .month(p.getMonth())
                .startDate(p.getStartDate())
                .endDate(p.getEndDate())
                .status(p.getStatus())
                .totalEmployees(p.getTotalEmployees())
                .totalGrossPay(p.getTotalGrossPay())
                .totalNetPay(p.getTotalNetPay())
                .totalEmployerCost(p.getTotalEmployerCost())
                .finalizedBy(p.getFinalizedBy() != null ? p.getFinalizedBy().getId() : null)
                .finalizedAt(p.getFinalizedAt())
                .build();
    }

    private PayrollAdjustmentDto toAdjustmentDto(PayrollAdjustment a) {
        return PayrollAdjustmentDto.builder()
                .id(a.getId())
                .employeeId(a.getEmployee().getId())
                .employeeName(a.getEmployee().getFirstName() + " " + a.getEmployee().getLastName())
                .payrollPeriodId(a.getPayrollPeriod() != null ? a.getPayrollPeriod().getId() : null)
                .adjustmentType(a.getAdjustmentType())
                .amount(a.getAmount())
                .reason(a.getReason())
                .isProcessed(a.getIsProcessed())
                .idempotencyKey(a.getIdempotencyKey())
                .authorizedBy(a.getAuthorizedBy() != null ? a.getAuthorizedBy().getId() : null)
                .build();
    }

    private PayslipDto toPayslipDto(Payslip p) {
        return PayslipDto.builder()
                .id(p.getId())
                .employeeId(p.getEmployee().getId())
                .employeeCode(p.getEmployee().getEmployeeCode())
                .employeeName(p.getEmployee().getFirstName() + " " + p.getEmployee().getLastName())
                .payrollPeriodId(p.getPayrollPeriod().getId())
                .year(p.getPayrollPeriod().getYear())
                .month(p.getPayrollPeriod().getMonth())
                .payslipNumber(p.getPayslipNumber())
                .issueDate(p.getIssueDate())
                .grossSalary(p.getGrossSalary())
                .totalDeductions(p.getTotalDeductions())
                .netSalary(p.getNetSalary())
                .paymentStatus(p.getPaymentStatus())
                .payslipDataJson(p.getPayslipDataJson())
                .build();
    }
}

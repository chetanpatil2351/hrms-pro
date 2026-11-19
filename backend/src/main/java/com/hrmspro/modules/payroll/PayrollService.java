
package com.hrmspro.modules.payroll;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.math.RoundingMode;
import com.hrmspro.modules.payroll.entity.SalaryAdvance;
import com.hrmspro.modules.payroll.entity.SalaryAdvanceStatus;
import com.hrmspro.modules.payroll.entity.SalaryAdvanceRepayment;
import com.hrmspro.modules.payroll.entity.PayrollLineItem;

import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollService {

    private final PayrollPeriodRepository periodRepository;
    private final SalaryAdvanceRepository salaryAdvanceRepository;
    private final SalaryAdvanceRepaymentRepository salaryAdvanceRepaymentRepository;
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

    // =========================================================
    // SALARY ADVANCE REPAYMENT INTEGRATION
    // =========================================================

    /**
     * Apply salary‑advance repayments for the given employee during a payroll run.
     * This method is called inside {@link #calculatePayrollRun(UUID, UUID)} for each
     * {@link PayrollRunEmployee}. It creates deduction line items, persists repayment
     * records and updates the advance status and outstanding balance.
     */
    private void applySalaryAdvanceRepayments(PayrollRunEmployee runEmp,
                                              PayrollRun run,
                                              UUID tenantId) {
        // Fetch eligible advances for the tenant
        List<SalaryAdvance> eligibleAdvances = salaryAdvanceRepository.findEligibleForPayrollRecovery(tenantId);

        // Payroll period end date determines when repayments start
        LocalDate periodEnd = run.getPayrollPeriod().getEndDate();

        for (SalaryAdvance advance : eligibleAdvances) {
            // Only advances belonging to the current employee
            if (!advance.getEmployee().getId().equals(runEmp.getEmployee().getId())) {
                continue;
            }
            // Repayment must have started
            if (advance.getRepaymentStartDate() == null ||
                advance.getRepaymentStartDate().isAfter(periodEnd)) {
                continue;
            }
            // Prevent duplicate deductions for the same payroll run
            if (salaryAdvanceRepaymentRepository.existsByAdvance_IdAndPayrollRun_Id(
                    advance.getId(), run.getId())) {
                continue;
            }

            // Determine deduction amount – never exceed outstanding balance
            BigDecimal installment = advance.getInstallmentAmount();
            if (installment == null) {
                installment = advance.getOutstandingBalance();
            }
            if (advance.getOutstandingBalance().compareTo(installment) < 0) {
                installment = advance.getOutstandingBalance();
            }

            // Create payroll line item for the deduction
            PayrollLineItem lineItem = PayrollLineItem.builder()
                    .payrollRunEmployee(runEmp)
                    .componentCode("SAL_ADV_DED")
                    .componentName("Salary Advance Repayment")
                    .type("DEDUCTION")
                    .amount(installment)
                    .build();
            lineItem.setTenantId(tenantId);
            lineItemRepository.save(lineItem);

            // Update employee totals for the repayment deduction
            runEmp.setTotalDeductions(runEmp.getTotalDeductions().add(installment));
            runEmp.setNetSalary(runEmp.getNetSalary().subtract(installment));
            // Persist the updated PayrollRunEmployee
            runEmployeeRepository.save(runEmp);

            // Create repayment record
            int installmentNumber = salaryAdvanceRepaymentRepository
                    .findByAdvance_IdAndTenantId(advance.getId(), tenantId)
                    .size() + 1;

            SalaryAdvanceRepayment repayment = SalaryAdvanceRepayment.builder()
                    .advance(advance)
                    .payrollRun(run)
                    .amount(installment)
                    .installmentNumber(installmentNumber)
                    .deductionDate(LocalDate.now())
                    .build();
            repayment.setTenantId(tenantId);
            salaryAdvanceRepaymentRepository.save(repayment);

            // Update advance balance and status
            advance.setOutstandingBalance(advance.getOutstandingBalance().subtract(installment));

            if (advance.getStatus() == SalaryAdvanceStatus.APPROVED) {
                advance.setStatus(SalaryAdvanceStatus.ACTIVE);
            }

            if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) == 0) {
                advance.setStatus(SalaryAdvanceStatus.FULLY_REPAID);
            } else if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0
                    && advance.getStatus() == SalaryAdvanceStatus.ACTIVE) {
                advance.setStatus(SalaryAdvanceStatus.PARTIALLY_REPAID);
            }

            salaryAdvanceRepository.save(advance);
        }
    }

    // =========================================================
    // STATUTORY RULES
    // =========================================================
    // SALARY ADVANCE REPAYMENT INTEGRATION
    // =========================================================

    /**
     * Apply salary‑advance repayments for the given employee during a payroll run.
     * This method is called inside {@link #calculatePayrollRun(UUID, UUID)} for each
     * {@link PayrollRunEmployee}. It creates deduction line items, persists repayment
     * records and updates the advance status and outstanding balance.
     */
    private void applySalaryAdvanceRepayments(PayrollRunEmployee runEmp,
                                              PayrollRun run,
                                              UUID tenantId) {
        // Fetch eligible advances for the tenant
        List<SalaryAdvance> eligibleAdvances = salaryAdvanceRepository.findEligibleForPayrollRecovery(tenantId);

        // Payroll period end date determines when repayments start
        LocalDate periodEnd = run.getPayrollPeriod().getEndDate();

        for (SalaryAdvance advance : eligibleAdvances) {
            // Only advances belonging to the current employee
            if (!advance.getEmployee().getId().equals(runEmp.getEmployee().getId())) {
                continue;
            }
            // Repayment must have started
            if (advance.getRepaymentStartDate() == null ||
                advance.getRepaymentStartDate().isAfter(periodEnd)) {
                continue;
            }
            // Prevent duplicate deductions for the same payroll run
            if (salaryAdvanceRepaymentRepository.existsByAdvance_IdAndPayrollRun_Id(
                    advance.getId(), run.getId())) {
                continue;
            }

            // Determine deduction amount – never exceed outstanding balance
            BigDecimal installment = advance.getInstallmentAmount();
            if (installment == null) {
                installment = advance.getOutstandingBalance();
            }
            if (advance.getOutstandingBalance().compareTo(installment) < 0) {
                installment = advance.getOutstandingBalance();
            }

            // Create payroll line item for the deduction
            PayrollLineItem lineItem = PayrollLineItem.builder()
                    .payrollRunEmployee(runEmp)
                    .componentCode("SAL_ADV_DED")
                    .componentName("Salary Advance Repayment")
                    .type("DEDUCTION")
                    .amount(installment)
                    .build();
            lineItem.setTenantId(tenantId);
            lineItemRepository.save(lineItem);

            // Update employee totals for the repayment deduction
            runEmp.setTotalDeductions(runEmp.getTotalDeductions().add(installment));
            runEmp.setNetSalary(runEmp.getNetSalary().subtract(installment));
            // Persist the updated PayrollRunEmployee
            runEmployeeRepository.save(runEmp);

            // Create repayment record
            int installmentNumber = salaryAdvanceRepaymentRepository
                    .findByAdvance_IdAndTenantId(advance.getId(), tenantId)
                    .size() + 1;

            SalaryAdvanceRepayment repayment = SalaryAdvanceRepayment.builder()
                    .advance(advance)
                    .payrollRun(run)
                    .amount(installment)
                    .installmentNumber(installmentNumber)
                    .deductionDate(LocalDate.now())
                    .build();
            repayment.setTenantId(tenantId);
            salaryAdvanceRepaymentRepository.save(repayment);

            // Update advance balance and status
            advance.setOutstandingBalance(advance.getOutstandingBalance().subtract(installment));

            if (advance.getStatus() == SalaryAdvanceStatus.APPROVED) {
                advance.setStatus(SalaryAdvanceStatus.ACTIVE);
            }

            if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) == 0) {
                advance.setStatus(SalaryAdvanceStatus.FULLY_REPAID);
            } else if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0
                    && advance.getStatus() == SalaryAdvanceStatus.ACTIVE) {
                advance.setStatus(SalaryAdvanceStatus.PARTIALLY_REPAID);
            }

            salaryAdvanceRepository.save(advance);
        }
    }

    // =========================================================

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
                .minimumWageThreshold(dto.getMinimumWageThreshold() != null
                        ? dto.getMinimumWageThreshold() : BigDecimal.ZERO)
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .effectiveFrom(dto.getEffectiveFrom() != null
                        ? dto.getEffectiveFrom() : LocalDate.of(1970, 1, 1))
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

    @Transactional(readOnly = true)
    public List<StatutoryRuleDto> getStatutoryRules(Integer year) {

        UUID tenantId = getRequiredTenantId();

        int targetYear = year != null ? year : LocalDate.now().getYear();

        return statutoryRuleRepository.findByTenantIdAndEffectiveYear(tenantId, targetYear)
                .stream()
                .map(this::toStatutoryRuleDto)
                .collect(Collectors.toList());
    }

    // =========================================================
    // SALARY ADVANCE REPAYMENT INTEGRATION
    // =========================================================

    /**
     * Apply salary‑advance repayments for the given employee during a payroll run.
     * This method is called inside {@link #calculatePayrollRun(UUID, UUID)} for each
     * {@link PayrollRunEmployee}. It creates deduction line items, persists repayment
     * records and updates the advance status and outstanding balance.
     */
    private void applySalaryAdvanceRepayments(PayrollRunEmployee runEmp,
                                              PayrollRun run,
                                              UUID tenantId) {
        // Fetch eligible advances for the tenant
        List<SalaryAdvance> eligibleAdvances = salaryAdvanceRepository.findEligibleForPayrollRecovery(tenantId);

        // Payroll period end date determines when repayments start
        LocalDate periodEnd = run.getPayrollPeriod().getEndDate();

        for (SalaryAdvance advance : eligibleAdvances) {
            // Only advances belonging to the current employee
            if (!advance.getEmployee().getId().equals(runEmp.getEmployee().getId())) {
                continue;
            }
            // Repayment must have started
            if (advance.getRepaymentStartDate() == null ||
                advance.getRepaymentStartDate().isAfter(periodEnd)) {
                continue;
            }
            // Prevent duplicate deductions for the same payroll run
            if (salaryAdvanceRepaymentRepository.existsByAdvance_IdAndPayrollRun_Id(
                    advance.getId(), run.getId())) {
                continue;
            }

            // Determine deduction amount – never exceed outstanding balance
            BigDecimal installment = advance.getInstallmentAmount();
            if (installment == null) {
                installment = advance.getOutstandingBalance();
            }
            if (advance.getOutstandingBalance().compareTo(installment) < 0) {
                installment = advance.getOutstandingBalance();
            }

            // Create payroll line item for the deduction
            PayrollLineItem lineItem = PayrollLineItem.builder()
                    .payrollRunEmployee(runEmp)
                    .componentCode("SAL_ADV_DED")
                    .componentName("Salary Advance Repayment")
                    .type("DEDUCTION")
                    .amount(installment)
                    .build();
            lineItem.setTenantId(tenantId);
            lineItemRepository.save(lineItem);

            // Update employee totals for the repayment deduction
            runEmp.setTotalDeductions(runEmp.getTotalDeductions().add(installment));
            runEmp.setNetSalary(runEmp.getNetSalary().subtract(installment));
            // Persist the updated PayrollRunEmployee
            runEmployeeRepository.save(runEmp);

            // Create repayment record
            int installmentNumber = salaryAdvanceRepaymentRepository
                    .findByAdvance_IdAndTenantId(advance.getId(), tenantId)
                    .size() + 1;

            SalaryAdvanceRepayment repayment = SalaryAdvanceRepayment.builder()
                    .advance(advance)
                    .payrollRun(run)
                    .amount(installment)
                    .installmentNumber(installmentNumber)
                    .deductionDate(LocalDate.now())
                    .build();
            repayment.setTenantId(tenantId);
            salaryAdvanceRepaymentRepository.save(repayment);

            // Update advance balance and status
            advance.setOutstandingBalance(advance.getOutstandingBalance().subtract(installment));

            if (advance.getStatus() == SalaryAdvanceStatus.APPROVED) {
                advance.setStatus(SalaryAdvanceStatus.ACTIVE);
            }

            if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) == 0) {
                advance.setStatus(SalaryAdvanceStatus.FULLY_REPAID);
            } else if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0
                    && advance.getStatus() == SalaryAdvanceStatus.ACTIVE) {
                advance.setStatus(SalaryAdvanceStatus.PARTIALLY_REPAID);
            }

            salaryAdvanceRepository.save(advance);
        }
    }

    // =========================================================
    // PAYROLL PERIOD MANAGEMENT
    // =========================================================
    // SALARY ADVANCE REPAYMENT INTEGRATION
    // =========================================================

    /**
     * Apply salary‑advance repayments for the given employee during a payroll run.
     * This method is called inside {@link #calculatePayrollRun(UUID, UUID)} for each
     * {@link PayrollRunEmployee}. It creates deduction line items, persists repayment
     * records and updates the advance status and outstanding balance.
     */
    private void applySalaryAdvanceRepayments(PayrollRunEmployee runEmp,
                                              PayrollRun run,
                                              UUID tenantId) {
        // Fetch eligible advances for the tenant
        List<SalaryAdvance> eligibleAdvances = salaryAdvanceRepository.findEligibleForPayrollRecovery(tenantId);

        // Payroll period end date determines when repayments start
        LocalDate periodEnd = run.getPayrollPeriod().getEndDate();

        for (SalaryAdvance advance : eligibleAdvances) {
            // Only advances belonging to the current employee
            if (!advance.getEmployee().getId().equals(runEmp.getEmployee().getId())) {
                continue;
            }
            // Repayment must have started
            if (advance.getRepaymentStartDate() == null ||
                advance.getRepaymentStartDate().isAfter(periodEnd)) {
                continue;
            }
            // Prevent duplicate deductions for the same payroll run
            if (salaryAdvanceRepaymentRepository.existsByAdvance_IdAndPayrollRun_Id(
                    advance.getId(), run.getId())) {
                continue;
            }

            // Determine deduction amount – never exceed outstanding balance
            BigDecimal installment = advance.getInstallmentAmount();
            if (installment == null) {
                installment = advance.getOutstandingBalance();
            }
            if (advance.getOutstandingBalance().compareTo(installment) < 0) {
                installment = advance.getOutstandingBalance();
            }

            // Create payroll line item for the deduction
            PayrollLineItem lineItem = PayrollLineItem.builder()
                    .payrollRunEmployee(runEmp)
                    .componentCode("SAL_ADV_DED")
                    .componentName("Salary Advance Repayment")
                    .type("DEDUCTION")
                    .amount(installment)
                    .build();
            lineItem.setTenantId(tenantId);
            lineItemRepository.save(lineItem);

            // Update employee totals for the repayment deduction
            runEmp.setTotalDeductions(runEmp.getTotalDeductions().add(installment));
            runEmp.setNetSalary(runEmp.getNetSalary().subtract(installment));
            // Persist the updated PayrollRunEmployee
            runEmployeeRepository.save(runEmp);

            // Create repayment record
            int installmentNumber = salaryAdvanceRepaymentRepository
                    .findByAdvance_IdAndTenantId(advance.getId(), tenantId)
                    .size() + 1;

            SalaryAdvanceRepayment repayment = SalaryAdvanceRepayment.builder()
                    .advance(advance)
                    .payrollRun(run)
                    .amount(installment)
                    .installmentNumber(installmentNumber)
                    .deductionDate(LocalDate.now())
                    .build();
            repayment.setTenantId(tenantId);
            salaryAdvanceRepaymentRepository.save(repayment);

            // Update advance balance and status
            advance.setOutstandingBalance(advance.getOutstandingBalance().subtract(installment));

            if (advance.getStatus() == SalaryAdvanceStatus.APPROVED) {
                advance.setStatus(SalaryAdvanceStatus.ACTIVE);
            }

            if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) == 0) {
                advance.setStatus(SalaryAdvanceStatus.FULLY_REPAID);
            } else if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0
                    && advance.getStatus() == SalaryAdvanceStatus.ACTIVE) {
                advance.setStatus(SalaryAdvanceStatus.PARTIALLY_REPAID);
            }

            salaryAdvanceRepository.save(advance);
        }
    }

    // =========================================================

    @Transactional
    public PayrollPeriodDto createPeriod(PayrollPeriodDto dto) {

        UUID tenantId = getRequiredTenantId();

        if (periodRepository.existsByTenantIdAndYearAndMonth(
                tenantId, dto.getYear(), dto.getMonth())) {
            throw new IllegalArgumentException(
                    "Payroll period for " + dto.getYear() + "-" + dto.getMonth() + " already exists");
        }

        YearMonth ym = YearMonth.of(dto.getYear(), dto.getMonth());

        PayrollPeriod period = PayrollPeriod.builder()
                .year(dto.getYear())
                .month(dto.getMonth())
                .startDate(ym.atDay(1))
                .endDate(ym.atEndOfMonth())
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

    @Transactional(readOnly = true)
    public Page<PayrollPeriodDto> getPeriods(Pageable pageable) {

        UUID tenantId = getRequiredTenantId();

        return periodRepository.findByTenantIdOrderByYearDescMonthDesc(tenantId, pageable)
                .map(this::toPeriodDto);
    }

    @Transactional(readOnly = true)
    public PayrollPeriodDto getPeriod(UUID periodId) {

        UUID tenantId = getRequiredTenantId();

        PayrollPeriod period = periodRepository.findByIdAndTenantId(periodId, tenantId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Payroll period not found: " + periodId));

        return toPeriodDto(period);
    }

    // =========================================================
    // SALARY ADVANCE REPAYMENT INTEGRATION
    // =========================================================

    /**
     * Apply salary‑advance repayments for the given employee during a payroll run.
     * This method is called inside {@link #calculatePayrollRun(UUID, UUID)} for each
     * {@link PayrollRunEmployee}. It creates deduction line items, persists repayment
     * records and updates the advance status and outstanding balance.
     */
    private void applySalaryAdvanceRepayments(PayrollRunEmployee runEmp,
                                              PayrollRun run,
                                              UUID tenantId) {
        // Fetch eligible advances for the tenant
        List<SalaryAdvance> eligibleAdvances = salaryAdvanceRepository.findEligibleForPayrollRecovery(tenantId);

        // Payroll period end date determines when repayments start
        LocalDate periodEnd = run.getPayrollPeriod().getEndDate();

        for (SalaryAdvance advance : eligibleAdvances) {
            // Only advances belonging to the current employee
            if (!advance.getEmployee().getId().equals(runEmp.getEmployee().getId())) {
                continue;
            }
            // Repayment must have started
            if (advance.getRepaymentStartDate() == null ||
                advance.getRepaymentStartDate().isAfter(periodEnd)) {
                continue;
            }
            // Prevent duplicate deductions for the same payroll run
            if (salaryAdvanceRepaymentRepository.existsByAdvance_IdAndPayrollRun_Id(
                    advance.getId(), run.getId())) {
                continue;
            }

            // Determine deduction amount – never exceed outstanding balance
            BigDecimal installment = advance.getInstallmentAmount();
            if (installment == null) {
                installment = advance.getOutstandingBalance();
            }
            if (advance.getOutstandingBalance().compareTo(installment) < 0) {
                installment = advance.getOutstandingBalance();
            }

            // Create payroll line item for the deduction
            PayrollLineItem lineItem = PayrollLineItem.builder()
                    .payrollRunEmployee(runEmp)
                    .componentCode("SAL_ADV_DED")
                    .componentName("Salary Advance Repayment")
                    .type("DEDUCTION")
                    .amount(installment)
                    .build();
            lineItem.setTenantId(tenantId);
            lineItemRepository.save(lineItem);

            // Update employee totals for the repayment deduction
            runEmp.setTotalDeductions(runEmp.getTotalDeductions().add(installment));
            runEmp.setNetSalary(runEmp.getNetSalary().subtract(installment));
            // Persist the updated PayrollRunEmployee
            runEmployeeRepository.save(runEmp);

            // Create repayment record
            int installmentNumber = salaryAdvanceRepaymentRepository
                    .findByAdvance_IdAndTenantId(advance.getId(), tenantId)
                    .size() + 1;

            SalaryAdvanceRepayment repayment = SalaryAdvanceRepayment.builder()
                    .advance(advance)
                    .payrollRun(run)
                    .amount(installment)
                    .installmentNumber(installmentNumber)
                    .deductionDate(LocalDate.now())
                    .build();
            repayment.setTenantId(tenantId);
            salaryAdvanceRepaymentRepository.save(repayment);

            // Update advance balance and status
            advance.setOutstandingBalance(advance.getOutstandingBalance().subtract(installment));

            if (advance.getStatus() == SalaryAdvanceStatus.APPROVED) {
                advance.setStatus(SalaryAdvanceStatus.ACTIVE);
            }

            if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) == 0) {
                advance.setStatus(SalaryAdvanceStatus.FULLY_REPAID);
            } else if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0
                    && advance.getStatus() == SalaryAdvanceStatus.ACTIVE) {
                advance.setStatus(SalaryAdvanceStatus.PARTIALLY_REPAID);
            }

            salaryAdvanceRepository.save(advance);
        }
    }

    // =========================================================
    // PAYROLL PREVIEW - NO DATABASE WRITES
    // =========================================================
    // SALARY ADVANCE REPAYMENT INTEGRATION
    // =========================================================

    /**
     * Apply salary‑advance repayments for the given employee during a payroll run.
     * This method is called inside {@link #calculatePayrollRun(UUID, UUID)} for each
     * {@link PayrollRunEmployee}. It creates deduction line items, persists repayment
     * records and updates the advance status and outstanding balance.
     */
    private void applySalaryAdvanceRepayments(PayrollRunEmployee runEmp,
                                              PayrollRun run,
                                              UUID tenantId) {
        // Fetch eligible advances for the tenant
        List<SalaryAdvance> eligibleAdvances = salaryAdvanceRepository.findEligibleForPayrollRecovery(tenantId);

        // Payroll period end date determines when repayments start
        LocalDate periodEnd = run.getPayrollPeriod().getEndDate();

        for (SalaryAdvance advance : eligibleAdvances) {
            // Only advances belonging to the current employee
            if (!advance.getEmployee().getId().equals(runEmp.getEmployee().getId())) {
                continue;
            }
            // Repayment must have started
            if (advance.getRepaymentStartDate() == null ||
                advance.getRepaymentStartDate().isAfter(periodEnd)) {
                continue;
            }
            // Prevent duplicate deductions for the same payroll run
            if (salaryAdvanceRepaymentRepository.existsByAdvance_IdAndPayrollRun_Id(
                    advance.getId(), run.getId())) {
                continue;
            }

            // Determine deduction amount – never exceed outstanding balance
            BigDecimal installment = advance.getInstallmentAmount();
            if (installment == null) {
                installment = advance.getOutstandingBalance();
            }
            if (advance.getOutstandingBalance().compareTo(installment) < 0) {
                installment = advance.getOutstandingBalance();
            }

            // Create payroll line item for the deduction
            PayrollLineItem lineItem = PayrollLineItem.builder()
                    .payrollRunEmployee(runEmp)
                    .componentCode("SAL_ADV_DED")
                    .componentName("Salary Advance Repayment")
                    .type("DEDUCTION")
                    .amount(installment)
                    .build();
            lineItem.setTenantId(tenantId);
            lineItemRepository.save(lineItem);

            // Update employee totals for the repayment deduction
            runEmp.setTotalDeductions(runEmp.getTotalDeductions().add(installment));
            runEmp.setNetSalary(runEmp.getNetSalary().subtract(installment));
            // Persist the updated PayrollRunEmployee
            runEmployeeRepository.save(runEmp);

            // Create repayment record
            int installmentNumber = salaryAdvanceRepaymentRepository
                    .findByAdvance_IdAndTenantId(advance.getId(), tenantId)
                    .size() + 1;

            SalaryAdvanceRepayment repayment = SalaryAdvanceRepayment.builder()
                    .advance(advance)
                    .payrollRun(run)
                    .amount(installment)
                    .installmentNumber(installmentNumber)
                    .deductionDate(LocalDate.now())
                    .build();
            repayment.setTenantId(tenantId);
            salaryAdvanceRepaymentRepository.save(repayment);

            // Update advance balance and status
            advance.setOutstandingBalance(advance.getOutstandingBalance().subtract(installment));

            if (advance.getStatus() == SalaryAdvanceStatus.APPROVED) {
                advance.setStatus(SalaryAdvanceStatus.ACTIVE);
            }

            if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) == 0) {
                advance.setStatus(SalaryAdvanceStatus.FULLY_REPAID);
            } else if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0
                    && advance.getStatus() == SalaryAdvanceStatus.ACTIVE) {
                advance.setStatus(SalaryAdvanceStatus.PARTIALLY_REPAID);
            }

            salaryAdvanceRepository.save(advance);
        }
    }

    // =========================================================

    @Transactional(readOnly = true)
    public PayrollPreviewDto previewPayrollRun(UUID periodId) {

        UUID tenantId = getRequiredTenantId();

        PayrollPeriod period = periodRepository.findByIdAndTenantId(periodId, tenantId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Payroll period not found: " + periodId));

        if ("FINALIZED".equals(period.getStatus())
                || "PAID".equals(period.getStatus())) {
            throw new IllegalStateException(
                    "Cannot preview payroll on a period with status: " + period.getStatus());
        }

        List<Employee> employees = employeeRepository.findAll().stream()
                .filter(e -> tenantId.equals(e.getTenantId()))
                .filter(e -> "ACTIVE".equalsIgnoreCase(e.getEmploymentStatus())
                        || "PROBATION".equalsIgnoreCase(e.getEmploymentStatus()))
                .filter(e -> e.getJoiningDate() != null
                        && !e.getJoiningDate().isAfter(period.getEndDate()))
                .collect(Collectors.toList());

        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalDeductions = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;

        List<PayrollPreviewDto.EmployeePayrollSummaryDto> summaries = new ArrayList<>();
        // placeholders for preview (no persistence)
        PayrollRun run = null;
        List<PayrollRunResultDto.FailedEmployeeDto> failures = new ArrayList<>();

        for (Employee emp : employees) {
            try {

                PayrollCalculationEngine.CalculationResult calc =
                        calculationEngine.calculateEmployeePayroll(tenantId, emp, period);

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

                List<PayrollRunResultDto.LineItemDto> lineItemDtos = calc.getLineItems().stream()
                        .map(li -> PayrollRunResultDto.LineItemDto.builder()
                                .componentCode(li.getComponentCode())
                                .componentName(li.getComponentName())
                                .type(li.getType())
                                .amount(li.getAmount())
                                .build())
                        .collect(Collectors.toList());






                // Record successful employee for count
            } catch (Exception ex) {
                log.warn("Skipping payroll calculation for employee {}: {}", emp.getEmployeeCode(), ex.getMessage());
                failures.add(new PayrollRunResultDto.FailedEmployeeDto(emp.getEmployeeCode(), ex.getMessage()));
            }
            try {

                PayrollCalculationEngine.CalculationResult calc =
                        calculationEngine.calculateEmployeePayroll(tenantId, emp, period);

                List<PayrollPreviewDto.LineItemDto> lineItems =
                        calc.getLineItems().stream()
                                .map(li -> PayrollPreviewDto.LineItemDto.builder()
                                        .componentCode(li.getComponentCode())
                                        .componentName(li.getComponentName())
                                        .type(li.getType())
                                        .amount(li.getAmount())
                                        .build())
                                .collect(Collectors.toList());

                summaries.add(
                        PayrollPreviewDto.EmployeePayrollSummaryDto.builder()
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
                                .lineItems(lineItems)
                                .build()
                );

                totalGross = totalGross.add(calc.getGrossEarnings());
                totalDeductions = totalDeductions.add(calc.getTotalDeductions());
                totalNet = totalNet.add(calc.getNetSalary());

            } catch (Exception ex) {
                log.warn("Skipping payroll preview for employee {}: {}",
                        emp.getEmployeeCode(), ex.getMessage());
            }
        }

        return PayrollPreviewDto.builder()
                .periodId(period.getId())
                .processedEmployees(summaries.size())
                .totalGross(totalGross)
                .totalDeductions(totalDeductions)
                .totalNet(totalNet)
                .employeeSummaries(summaries)
                .build();
    }

    // =========================================================
    // SALARY ADVANCE REPAYMENT INTEGRATION
    // =========================================================

    /**
     * Apply salary‑advance repayments for the given employee during a payroll run.
     * This method is called inside {@link #calculatePayrollRun(UUID, UUID)} for each
     * {@link PayrollRunEmployee}. It creates deduction line items, persists repayment
     * records and updates the advance status and outstanding balance.
     */
    private void applySalaryAdvanceRepayments(PayrollRunEmployee runEmp,
                                              PayrollRun run,
                                              UUID tenantId) {
        // Fetch eligible advances for the tenant
        List<SalaryAdvance> eligibleAdvances = salaryAdvanceRepository.findEligibleForPayrollRecovery(tenantId);

        // Payroll period end date determines when repayments start
        LocalDate periodEnd = run.getPayrollPeriod().getEndDate();

        for (SalaryAdvance advance : eligibleAdvances) {
            // Only advances belonging to the current employee
            if (!advance.getEmployee().getId().equals(runEmp.getEmployee().getId())) {
                continue;
            }
            // Repayment must have started
            if (advance.getRepaymentStartDate() == null ||
                advance.getRepaymentStartDate().isAfter(periodEnd)) {
                continue;
            }
            // Prevent duplicate deductions for the same payroll run
            if (salaryAdvanceRepaymentRepository.existsByAdvance_IdAndPayrollRun_Id(
                    advance.getId(), run.getId())) {
                continue;
            }

            // Determine deduction amount – never exceed outstanding balance
            BigDecimal installment = advance.getInstallmentAmount();
            if (installment == null) {
                installment = advance.getOutstandingBalance();
            }
            if (advance.getOutstandingBalance().compareTo(installment) < 0) {
                installment = advance.getOutstandingBalance();
            }

            // Create payroll line item for the deduction
            PayrollLineItem lineItem = PayrollLineItem.builder()
                    .payrollRunEmployee(runEmp)
                    .componentCode("SAL_ADV_DED")
                    .componentName("Salary Advance Repayment")
                    .type("DEDUCTION")
                    .amount(installment)
                    .build();
            lineItem.setTenantId(tenantId);
            lineItemRepository.save(lineItem);

            // Update employee totals for the repayment deduction
            runEmp.setTotalDeductions(runEmp.getTotalDeductions().add(installment));
            runEmp.setNetSalary(runEmp.getNetSalary().subtract(installment));
            // Persist the updated PayrollRunEmployee
            runEmployeeRepository.save(runEmp);

            // Create repayment record
            int installmentNumber = salaryAdvanceRepaymentRepository
                    .findByAdvance_IdAndTenantId(advance.getId(), tenantId)
                    .size() + 1;

            SalaryAdvanceRepayment repayment = SalaryAdvanceRepayment.builder()
                    .advance(advance)
                    .payrollRun(run)
                    .amount(installment)
                    .installmentNumber(installmentNumber)
                    .deductionDate(LocalDate.now())
                    .build();
            repayment.setTenantId(tenantId);
            salaryAdvanceRepaymentRepository.save(repayment);

            // Update advance balance and status
            advance.setOutstandingBalance(advance.getOutstandingBalance().subtract(installment));

            if (advance.getStatus() == SalaryAdvanceStatus.APPROVED) {
                advance.setStatus(SalaryAdvanceStatus.ACTIVE);
            }

            if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) == 0) {
                advance.setStatus(SalaryAdvanceStatus.FULLY_REPAID);
            } else if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0
                    && advance.getStatus() == SalaryAdvanceStatus.ACTIVE) {
                advance.setStatus(SalaryAdvanceStatus.PARTIALLY_REPAID);
            }

            salaryAdvanceRepository.save(advance);
        }
    }

    // =========================================================
    // PAYROLL RUN HISTORY
    // =========================================================
    // SALARY ADVANCE REPAYMENT INTEGRATION
    // =========================================================

    /**
     * Apply salary‑advance repayments for the given employee during a payroll run.
     * This method is called inside {@link #calculatePayrollRun(UUID, UUID)} for each
     * {@link PayrollRunEmployee}. It creates deduction line items, persists repayment
     * records and updates the advance status and outstanding balance.
     */
    private void applySalaryAdvanceRepayments(PayrollRunEmployee runEmp,
                                              PayrollRun run,
                                              UUID tenantId) {
        // Fetch eligible advances for the tenant
        List<SalaryAdvance> eligibleAdvances = salaryAdvanceRepository.findEligibleForPayrollRecovery(tenantId);

        // Payroll period end date determines when repayments start
        LocalDate periodEnd = run.getPayrollPeriod().getEndDate();

        for (SalaryAdvance advance : eligibleAdvances) {
            // Only advances belonging to the current employee
            if (!advance.getEmployee().getId().equals(runEmp.getEmployee().getId())) {
                continue;
            }
            // Repayment must have started
            if (advance.getRepaymentStartDate() == null ||
                advance.getRepaymentStartDate().isAfter(periodEnd)) {
                continue;
            }
            // Prevent duplicate deductions for the same payroll run
            if (salaryAdvanceRepaymentRepository.existsByAdvance_IdAndPayrollRun_Id(
                    advance.getId(), run.getId())) {
                continue;
            }

            // Determine deduction amount – never exceed outstanding balance
            BigDecimal installment = advance.getInstallmentAmount();
            if (installment == null) {
                installment = advance.getOutstandingBalance();
            }
            if (advance.getOutstandingBalance().compareTo(installment) < 0) {
                installment = advance.getOutstandingBalance();
            }

            // Create payroll line item for the deduction
            PayrollLineItem lineItem = PayrollLineItem.builder()
                    .payrollRunEmployee(runEmp)
                    .componentCode("SAL_ADV_DED")
                    .componentName("Salary Advance Repayment")
                    .type("DEDUCTION")
                    .amount(installment)
                    .build();
            lineItem.setTenantId(tenantId);
            lineItemRepository.save(lineItem);

            // Update employee totals for the repayment deduction
            runEmp.setTotalDeductions(runEmp.getTotalDeductions().add(installment));
            runEmp.setNetSalary(runEmp.getNetSalary().subtract(installment));
            // Persist the updated PayrollRunEmployee
            runEmployeeRepository.save(runEmp);

            // Create repayment record
            int installmentNumber = salaryAdvanceRepaymentRepository
                    .findByAdvance_IdAndTenantId(advance.getId(), tenantId)
                    .size() + 1;

            SalaryAdvanceRepayment repayment = SalaryAdvanceRepayment.builder()
                    .advance(advance)
                    .payrollRun(run)
                    .amount(installment)
                    .installmentNumber(installmentNumber)
                    .deductionDate(LocalDate.now())
                    .build();
            repayment.setTenantId(tenantId);
            salaryAdvanceRepaymentRepository.save(repayment);

            // Update advance balance and status
            advance.setOutstandingBalance(advance.getOutstandingBalance().subtract(installment));

            if (advance.getStatus() == SalaryAdvanceStatus.APPROVED) {
                advance.setStatus(SalaryAdvanceStatus.ACTIVE);
            }

            if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) == 0) {
                advance.setStatus(SalaryAdvanceStatus.FULLY_REPAID);
            } else if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0
                    && advance.getStatus() == SalaryAdvanceStatus.ACTIVE) {
                advance.setStatus(SalaryAdvanceStatus.PARTIALLY_REPAID);
            }

            salaryAdvanceRepository.save(advance);
        }
    }

    // =========================================================

    @Transactional(readOnly = true)
    public List<PayrollRunDto> listRuns(UUID periodId) {

        UUID tenantId = getRequiredTenantId();

        PayrollPeriod period = periodRepository.findByIdAndTenantId(periodId, tenantId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Payroll period not found: " + periodId));

        return runRepository.findByPayrollPeriodIdOrderByRunNumberDesc(period.getId())
                .stream()
                .map(run -> {

                    List<PayrollRunEmployee> employees = run.getEmployees();

                    BigDecimal gross = employees.stream()
                            .map(PayrollRunEmployee::getGrossEarnings)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal deductions = employees.stream()
                            .map(PayrollRunEmployee::getTotalDeductions)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal net = employees.stream()
                            .map(PayrollRunEmployee::getNetSalary)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    return PayrollRunDto.builder()
                            .runId(run.getId())
                            .periodId(period.getId())
                            .runNumber(run.getRunNumber())
                            .runType(run.getRunType())
                            .status(run.getStatus())
                            .createdAt(run.getCreatedAt())
                            .completedAt(run.getCompletedAt())
                            .processedEmployees(employees.size())
                            .totalGross(gross)
                            .totalDeductions(deductions)
                            .totalNet(net)
                            .build();
                })
                .collect(Collectors.toList());
    }

    // =========================================================
    // SALARY ADVANCE REPAYMENT INTEGRATION
    // =========================================================

    /**
     * Apply salary‑advance repayments for the given employee during a payroll run.
     * This method is called inside {@link #calculatePayrollRun(UUID, UUID)} for each
     * {@link PayrollRunEmployee}. It creates deduction line items, persists repayment
     * records and updates the advance status and outstanding balance.
     */
    private void applySalaryAdvanceRepayments(PayrollRunEmployee runEmp,
                                              PayrollRun run,
                                              UUID tenantId) {
        // Fetch eligible advances for the tenant
        List<SalaryAdvance> eligibleAdvances = salaryAdvanceRepository.findEligibleForPayrollRecovery(tenantId);

        // Payroll period end date determines when repayments start
        LocalDate periodEnd = run.getPayrollPeriod().getEndDate();

        for (SalaryAdvance advance : eligibleAdvances) {
            // Only advances belonging to the current employee
            if (!advance.getEmployee().getId().equals(runEmp.getEmployee().getId())) {
                continue;
            }
            // Repayment must have started
            if (advance.getRepaymentStartDate() == null ||
                advance.getRepaymentStartDate().isAfter(periodEnd)) {
                continue;
            }
            // Prevent duplicate deductions for the same payroll run
            if (salaryAdvanceRepaymentRepository.existsByAdvance_IdAndPayrollRun_Id(
                    advance.getId(), run.getId())) {
                continue;
            }

            // Determine deduction amount – never exceed outstanding balance
            BigDecimal installment = advance.getInstallmentAmount();
            if (installment == null) {
                installment = advance.getOutstandingBalance();
            }
            if (advance.getOutstandingBalance().compareTo(installment) < 0) {
                installment = advance.getOutstandingBalance();
            }

            // Create payroll line item for the deduction
            PayrollLineItem lineItem = PayrollLineItem.builder()
                    .payrollRunEmployee(runEmp)
                    .componentCode("SAL_ADV_DED")
                    .componentName("Salary Advance Repayment")
                    .type("DEDUCTION")
                    .amount(installment)
                    .build();
            lineItem.setTenantId(tenantId);
            lineItemRepository.save(lineItem);

            // Update employee totals for the repayment deduction
            runEmp.setTotalDeductions(runEmp.getTotalDeductions().add(installment));
            runEmp.setNetSalary(runEmp.getNetSalary().subtract(installment));
            // Persist the updated PayrollRunEmployee
            runEmployeeRepository.save(runEmp);

            // Create repayment record
            int installmentNumber = salaryAdvanceRepaymentRepository
                    .findByAdvance_IdAndTenantId(advance.getId(), tenantId)
                    .size() + 1;

            SalaryAdvanceRepayment repayment = SalaryAdvanceRepayment.builder()
                    .advance(advance)
                    .payrollRun(run)
                    .amount(installment)
                    .installmentNumber(installmentNumber)
                    .deductionDate(LocalDate.now())
                    .build();
            repayment.setTenantId(tenantId);
            salaryAdvanceRepaymentRepository.save(repayment);

            // Update advance balance and status
            advance.setOutstandingBalance(advance.getOutstandingBalance().subtract(installment));

            if (advance.getStatus() == SalaryAdvanceStatus.APPROVED) {
                advance.setStatus(SalaryAdvanceStatus.ACTIVE);
            }

            if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) == 0) {
                advance.setStatus(SalaryAdvanceStatus.FULLY_REPAID);
            } else if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0
                    && advance.getStatus() == SalaryAdvanceStatus.ACTIVE) {
                advance.setStatus(SalaryAdvanceStatus.PARTIALLY_REPAID);
            }

            salaryAdvanceRepository.save(advance);
        }
    }

    // =========================================================
    // PAYROLL RUN DETAILS
    // =========================================================
    // SALARY ADVANCE REPAYMENT INTEGRATION
    // =========================================================

    /**
     * Apply salary‑advance repayments for the given employee during a payroll run.
     * This method is called inside {@link #calculatePayrollRun(UUID, UUID)} for each
     * {@link PayrollRunEmployee}. It creates deduction line items, persists repayment
     * records and updates the advance status and outstanding balance.
     */
    private void applySalaryAdvanceRepayments(PayrollRunEmployee runEmp,
                                              PayrollRun run,
                                              UUID tenantId) {
        // Fetch eligible advances for the tenant
        List<SalaryAdvance> eligibleAdvances = salaryAdvanceRepository.findEligibleForPayrollRecovery(tenantId);

        // Payroll period end date determines when repayments start
        LocalDate periodEnd = run.getPayrollPeriod().getEndDate();

        for (SalaryAdvance advance : eligibleAdvances) {
            // Only advances belonging to the current employee
            if (!advance.getEmployee().getId().equals(runEmp.getEmployee().getId())) {
                continue;
            }
            // Repayment must have started
            if (advance.getRepaymentStartDate() == null ||
                advance.getRepaymentStartDate().isAfter(periodEnd)) {
                continue;
            }
            // Prevent duplicate deductions for the same payroll run
            if (salaryAdvanceRepaymentRepository.existsByAdvance_IdAndPayrollRun_Id(
                    advance.getId(), run.getId())) {
                continue;
            }

            // Determine deduction amount – never exceed outstanding balance
            BigDecimal installment = advance.getInstallmentAmount();
            if (installment == null) {
                installment = advance.getOutstandingBalance();
            }
            if (advance.getOutstandingBalance().compareTo(installment) < 0) {
                installment = advance.getOutstandingBalance();
            }

            // Create payroll line item for the deduction
            PayrollLineItem lineItem = PayrollLineItem.builder()
                    .payrollRunEmployee(runEmp)
                    .componentCode("SAL_ADV_DED")
                    .componentName("Salary Advance Repayment")
                    .type("DEDUCTION")
                    .amount(installment)
                    .build();
            lineItem.setTenantId(tenantId);
            lineItemRepository.save(lineItem);

            // Update employee totals for the repayment deduction
            runEmp.setTotalDeductions(runEmp.getTotalDeductions().add(installment));
            runEmp.setNetSalary(runEmp.getNetSalary().subtract(installment));
            // Persist the updated PayrollRunEmployee
            runEmployeeRepository.save(runEmp);

            // Create repayment record
            int installmentNumber = salaryAdvanceRepaymentRepository
                    .findByAdvance_IdAndTenantId(advance.getId(), tenantId)
                    .size() + 1;

            SalaryAdvanceRepayment repayment = SalaryAdvanceRepayment.builder()
                    .advance(advance)
                    .payrollRun(run)
                    .amount(installment)
                    .installmentNumber(installmentNumber)
                    .deductionDate(LocalDate.now())
                    .build();
            repayment.setTenantId(tenantId);
            salaryAdvanceRepaymentRepository.save(repayment);

            // Update advance balance and status
            advance.setOutstandingBalance(advance.getOutstandingBalance().subtract(installment));

            if (advance.getStatus() == SalaryAdvanceStatus.APPROVED) {
                advance.setStatus(SalaryAdvanceStatus.ACTIVE);
            }

            if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) == 0) {
                advance.setStatus(SalaryAdvanceStatus.FULLY_REPAID);
            } else if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0
                    && advance.getStatus() == SalaryAdvanceStatus.ACTIVE) {
                advance.setStatus(SalaryAdvanceStatus.PARTIALLY_REPAID);
            }

            salaryAdvanceRepository.save(advance);
        }
    }

    // =========================================================

    @Transactional(readOnly = true)
    public PayrollRunResultDto getRunDetails(UUID runId) {

        UUID tenantId = getRequiredTenantId();

        PayrollRun run = runRepository.findByIdAndTenantId(runId, tenantId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Payroll run not found: " + runId));

        List<PayrollRunEmployee> runEmployees =
                runEmployeeRepository.findByPayrollRunId(run.getId());

        List<PayrollRunResultDto.EmployeePayrollSummaryDto> summaries = new ArrayList<>();
        List<PayrollRunResultDto.FailedEmployeeDto> failures = new ArrayList<>();

        for (PayrollRunEmployee re : runEmployees) {

            List<PayrollRunResultDto.LineItemDto> lineItemDtos =
                    lineItemRepository.findByPayrollRunEmployeeId(re.getId())
                            .stream()
                            .map(li -> PayrollRunResultDto.LineItemDto.builder()
                                    .componentCode(li.getComponentCode())
                                    .componentName(li.getComponentName())
                                    .type(li.getType())
                                    .amount(li.getAmount())
                                    .build())
                            .collect(Collectors.toList());

            summaries.add(
                    PayrollRunResultDto.EmployeePayrollSummaryDto.builder()
                            .employeeId(re.getEmployee().getId())
                            .employeeCode(re.getEmployee().getEmployeeCode())
                            .employeeName(re.getEmployee().getFirstName()
                                    + " " + re.getEmployee().getLastName())
                            .totalDays(re.getTotalDays())
                            .payableDays(re.getPayableDays())
                            .unpaidLeaveDays(re.getUnpaidLeaveDays())
                            .grossEarnings(re.getGrossEarnings())
                            .totalDeductions(re.getTotalDeductions())
                            .employerContributions(re.getEmployerContributions())
                            .netSalary(re.getNetSalary())
                            .lineItems(lineItemDtos)
                            .build()
            );
        }

        BigDecimal gross = runEmployees.stream()
                .map(PayrollRunEmployee::getGrossEarnings)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal deductions = runEmployees.stream()
                .map(PayrollRunEmployee::getTotalDeductions)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal net = runEmployees.stream()
                .map(PayrollRunEmployee::getNetSalary)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return PayrollRunResultDto.builder()
                .runId(run.getId())
                .periodId(run.getPayrollPeriod().getId())
                .runNumber(run.getRunNumber())
                .runType(run.getRunType())
                .status(run.getStatus())
                .processedEmployees(summaries.size())
                .totalGross(gross)
                .totalDeductions(deductions)
                .totalNet(net)
                .employeeSummaries(summaries)
                .build();
    }

    // =========================================================
    // SALARY ADVANCE REPAYMENT INTEGRATION
    // =========================================================

    /**
     * Apply salary‑advance repayments for the given employee during a payroll run.
     * This method is called inside {@link #calculatePayrollRun(UUID, UUID)} for each
     * {@link PayrollRunEmployee}. It creates deduction line items, persists repayment
     * records and updates the advance status and outstanding balance.
     */
    private void applySalaryAdvanceRepayments(PayrollRunEmployee runEmp,
                                              PayrollRun run,
                                              UUID tenantId) {
        // Fetch eligible advances for the tenant
        List<SalaryAdvance> eligibleAdvances = salaryAdvanceRepository.findEligibleForPayrollRecovery(tenantId);

        // Payroll period end date determines when repayments start
        LocalDate periodEnd = run.getPayrollPeriod().getEndDate();

        for (SalaryAdvance advance : eligibleAdvances) {
            // Only advances belonging to the current employee
            if (!advance.getEmployee().getId().equals(runEmp.getEmployee().getId())) {
                continue;
            }
            // Repayment must have started
            if (advance.getRepaymentStartDate() == null ||
                advance.getRepaymentStartDate().isAfter(periodEnd)) {
                continue;
            }
            // Prevent duplicate deductions for the same payroll run
            if (salaryAdvanceRepaymentRepository.existsByAdvance_IdAndPayrollRun_Id(
                    advance.getId(), run.getId())) {
                continue;
            }

            // Determine deduction amount – never exceed outstanding balance
            BigDecimal installment = advance.getInstallmentAmount();
            if (installment == null) {
                installment = advance.getOutstandingBalance();
            }
            if (advance.getOutstandingBalance().compareTo(installment) < 0) {
                installment = advance.getOutstandingBalance();
            }

            // Create payroll line item for the deduction
            PayrollLineItem lineItem = PayrollLineItem.builder()
                    .payrollRunEmployee(runEmp)
                    .componentCode("SAL_ADV_DED")
                    .componentName("Salary Advance Repayment")
                    .type("DEDUCTION")
                    .amount(installment)
                    .build();
            lineItem.setTenantId(tenantId);
            lineItemRepository.save(lineItem);

            // Update employee totals for the repayment deduction
            runEmp.setTotalDeductions(runEmp.getTotalDeductions().add(installment));
            runEmp.setNetSalary(runEmp.getNetSalary().subtract(installment));
            // Persist the updated PayrollRunEmployee
            runEmployeeRepository.save(runEmp);

            // Create repayment record
            int installmentNumber = salaryAdvanceRepaymentRepository
                    .findByAdvance_IdAndTenantId(advance.getId(), tenantId)
                    .size() + 1;

            SalaryAdvanceRepayment repayment = SalaryAdvanceRepayment.builder()
                    .advance(advance)
                    .payrollRun(run)
                    .amount(installment)
                    .installmentNumber(installmentNumber)
                    .deductionDate(LocalDate.now())
                    .build();
            repayment.setTenantId(tenantId);
            salaryAdvanceRepaymentRepository.save(repayment);

            // Update advance balance and status
            advance.setOutstandingBalance(advance.getOutstandingBalance().subtract(installment));

            if (advance.getStatus() == SalaryAdvanceStatus.APPROVED) {
                advance.setStatus(SalaryAdvanceStatus.ACTIVE);
            }

            if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) == 0) {
                advance.setStatus(SalaryAdvanceStatus.FULLY_REPAID);
            } else if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0
                    && advance.getStatus() == SalaryAdvanceStatus.ACTIVE) {
                advance.setStatus(SalaryAdvanceStatus.PARTIALLY_REPAID);
            }

            salaryAdvanceRepository.save(advance);
        }
    }

    // =========================================================
    // CALCULATE PAYROLL
    // =========================================================
    // SALARY ADVANCE REPAYMENT INTEGRATION
    // =========================================================

    /**
     * Apply salary‑advance repayments for the given employee during a payroll run.
     * This method is called inside {@link #calculatePayrollRun(UUID, UUID)} for each
     * {@link PayrollRunEmployee}. It creates deduction line items, persists repayment
     * records and updates the advance status and outstanding balance.
     */
    private void applySalaryAdvanceRepayments(PayrollRunEmployee runEmp,
                                              PayrollRun run,
                                              UUID tenantId) {
        // Fetch eligible advances for the tenant
        List<SalaryAdvance> eligibleAdvances = salaryAdvanceRepository.findEligibleForPayrollRecovery(tenantId);

        // Payroll period end date determines when repayments start
        LocalDate periodEnd = run.getPayrollPeriod().getEndDate();

        for (SalaryAdvance advance : eligibleAdvances) {
            // Only advances belonging to the current employee
            if (!advance.getEmployee().getId().equals(runEmp.getEmployee().getId())) {
                continue;
            }
            // Repayment must have started
            if (advance.getRepaymentStartDate() == null ||
                advance.getRepaymentStartDate().isAfter(periodEnd)) {
                continue;
            }
            // Prevent duplicate deductions for the same payroll run
            if (salaryAdvanceRepaymentRepository.existsByAdvance_IdAndPayrollRun_Id(
                    advance.getId(), run.getId())) {
                continue;
            }

            // Determine deduction amount – never exceed outstanding balance
            BigDecimal installment = advance.getInstallmentAmount();
            if (installment == null) {
                installment = advance.getOutstandingBalance();
            }
            if (advance.getOutstandingBalance().compareTo(installment) < 0) {
                installment = advance.getOutstandingBalance();
            }

            // Create payroll line item for the deduction
            PayrollLineItem lineItem = PayrollLineItem.builder()
                    .payrollRunEmployee(runEmp)
                    .componentCode("SAL_ADV_DED")
                    .componentName("Salary Advance Repayment")
                    .type("DEDUCTION")
                    .amount(installment)
                    .build();
            lineItem.setTenantId(tenantId);
            lineItemRepository.save(lineItem);

            // Update employee totals for the repayment deduction
            runEmp.setTotalDeductions(runEmp.getTotalDeductions().add(installment));
            runEmp.setNetSalary(runEmp.getNetSalary().subtract(installment));
            // Persist the updated PayrollRunEmployee
            runEmployeeRepository.save(runEmp);

            // Create repayment record
            int installmentNumber = salaryAdvanceRepaymentRepository
                    .findByAdvance_IdAndTenantId(advance.getId(), tenantId)
                    .size() + 1;

            SalaryAdvanceRepayment repayment = SalaryAdvanceRepayment.builder()
                    .advance(advance)
                    .payrollRun(run)
                    .amount(installment)
                    .installmentNumber(installmentNumber)
                    .deductionDate(LocalDate.now())
                    .build();
            repayment.setTenantId(tenantId);
            salaryAdvanceRepaymentRepository.save(repayment);

            // Update advance balance and status
            advance.setOutstandingBalance(advance.getOutstandingBalance().subtract(installment));

            if (advance.getStatus() == SalaryAdvanceStatus.APPROVED) {
                advance.setStatus(SalaryAdvanceStatus.ACTIVE);
            }

            if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) == 0) {
                advance.setStatus(SalaryAdvanceStatus.FULLY_REPAID);
            } else if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0
                    && advance.getStatus() == SalaryAdvanceStatus.ACTIVE) {
                advance.setStatus(SalaryAdvanceStatus.PARTIALLY_REPAID);
            }

            salaryAdvanceRepository.save(advance);
        }
    }

    // =========================================================

    @Transactional(rollbackFor = JsonProcessingException.class)
    public PayrollRunResultDto calculatePayrollRun(UUID periodId, UUID currentUserId) {

        UUID tenantId = getRequiredTenantId();

        PayrollPeriod period = periodRepository.findByIdAndTenantId(periodId, tenantId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Payroll period not found: " + periodId));

        if ("FINALIZED".equals(period.getStatus())
                || "PAID".equals(period.getStatus())) {
            throw new IllegalStateException(
                    "Cannot re-run payroll on a period with status: " + period.getStatus());
        }

        if ("REVIEW".equals(period.getStatus())) {
            throw new IllegalStateException(
                    "Payroll for this period has already been calculated (status REVIEW). Use approve/finalize instead.");
        }

        period.setStatus("PROCESSING");

        AppUser initiator = currentUserId != null
                ? appUserRepository.findById(currentUserId).orElse(null)
                : null;

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

        List<Employee> employees = employeeRepository.findAll().stream()
                .filter(e -> tenantId.equals(e.getTenantId()))
                .filter(e -> "ACTIVE".equalsIgnoreCase(e.getEmploymentStatus())
                        || "PROBATION".equalsIgnoreCase(e.getEmploymentStatus()))
                .filter(e -> e.getJoiningDate() != null
                        && !e.getJoiningDate().isAfter(period.getEndDate()))
                .collect(Collectors.toList());

        BigDecimal runTotalGross = BigDecimal.ZERO;
        BigDecimal runTotalDeductions = BigDecimal.ZERO;
        BigDecimal runTotalNet = BigDecimal.ZERO;
        BigDecimal runTotalEmployerCost = BigDecimal.ZERO;

        List<PayrollRunResultDto.EmployeePayrollSummaryDto> summaries = new ArrayList<>();
        List<PayrollRunResultDto.FailedEmployeeDto> failures = new ArrayList<>();

        for (Employee emp : employees) {
            try {

                PayrollCalculationEngine.CalculationResult calc =
                        calculationEngine.calculateEmployeePayroll(tenantId, emp, period);

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

                List<PayrollRunResultDto.LineItemDto> lineItemDtos = calc.getLineItems().stream()
                        .map(li -> PayrollRunResultDto.LineItemDto.builder()
                                .componentCode(li.getComponentCode())
                                .componentName(li.getComponentName())
                                .type(li.getType())
                                .amount(li.getAmount())
                                .build())
                        .collect(Collectors.toList());






                // Record successful employee for count
            } catch (Exception ex) {
                log.warn("Skipping payroll calculation for employee {}: {}", emp.getEmployeeCode(), ex.getMessage());
                failures.add(new PayrollRunResultDto.FailedEmployeeDto(emp.getEmployeeCode(), ex.getMessage()));
            }
            try {

                PayrollCalculationEngine.CalculationResult calc =
                        calculationEngine.calculateEmployeePayroll(tenantId, emp, period);

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

                PayrollCalculationSnapshot snapshot =
                        PayrollCalculationSnapshot.builder()
                                .payrollRunEmployee(runEmp)
                                .snapshotJson(calc.getSnapshotJson())
                                .build();

                snapshot.setTenantId(tenantId);
                snapshotRepository.save(snapshot);

                applySalaryAdvanceRepayments(runEmp, run, tenantId);
                runTotalGross = runTotalGross.add(calc.getGrossEarnings());
                runTotalDeductions = runTotalDeductions.add(calc.getTotalDeductions());
                runTotalNet = runTotalNet.add(calc.getNetSalary());
                runTotalEmployerCost =
                        runTotalEmployerCost.add(calc.getEmployerContributions());

                List<PayrollRunResultDto.LineItemDto> lineItemDtos =
                        calc.getLineItems().stream()
                                .map(li -> PayrollRunResultDto.LineItemDto.builder()
                                        .componentCode(li.getComponentCode())
                                        .componentName(li.getComponentName())
                                        .type(li.getType())
                                        .amount(li.getAmount())
                                        .build())
                                .collect(Collectors.toList());

                summaries.add(
                        PayrollRunResultDto.EmployeePayrollSummaryDto.builder()
                                .employeeId(emp.getId())
                                .employeeCode(emp.getEmployeeCode())
                                .employeeName(emp.getFirstName()
                                        + " " + emp.getLastName())
                                .totalDays(calc.getTotalDaysInMonth())
                                .payableDays(calc.getPayableDays())
                                .unpaidLeaveDays(calc.getUnpaidLeaveDays())
                                .grossEarnings(calc.getGrossEarnings())
                                .totalDeductions(calc.getTotalDeductions())
                                .employerContributions(calc.getEmployerContributions())
                                .netSalary(calc.getNetSalary())
                                .lineItems(lineItemDtos)
                                .build()
                );

            } catch (Exception ex) {
                log.warn("Skipping payroll calculation for employee {}: {}",
                        emp.getEmployeeCode(), ex.getMessage());
            }
        }

        // Adjust aggregate totals to include salary‑advance repayments
        // Sum all repayment amounts for this payroll run (tenant‑scoped)
        BigDecimal repaymentTotal = salaryAdvanceRepaymentRepository
                .findByPayrollRun_IdAndTenantId(run.getId(), tenantId)
                .stream()
                .map(SalaryAdvanceRepayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Add repayments to deductions and subtract from net salary
        runTotalDeductions = runTotalDeductions.add(repaymentTotal);
        runTotalNet = runTotalNet.subtract(repaymentTotal);

        run.setStatus("COMPLETED");
        run.setCompletedAt(Instant.now());
        runRepository.save(run);

        period.setStatus("REVIEW");
        period.setTotalEmployees(summaries.size());
        period.setTotalGrossPay(runTotalGross);
        period.setTotalNetPay(runTotalNet);
        period.setTotalEmployerCost(runTotalEmployerCost);

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

    // =========================================================
    // SALARY ADVANCE REPAYMENT INTEGRATION
    // =========================================================

    /**
     * Apply salary‑advance repayments for the given employee during a payroll run.
     * This method is called inside {@link #calculatePayrollRun(UUID, UUID)} for each
     * {@link PayrollRunEmployee}. It creates deduction line items, persists repayment
     * records and updates the advance status and outstanding balance.
     */
    private void applySalaryAdvanceRepayments(PayrollRunEmployee runEmp,
                                              PayrollRun run,
                                              UUID tenantId) {
        // Fetch eligible advances for the tenant
        List<SalaryAdvance> eligibleAdvances = salaryAdvanceRepository.findEligibleForPayrollRecovery(tenantId);

        // Payroll period end date determines when repayments start
        LocalDate periodEnd = run.getPayrollPeriod().getEndDate();

        for (SalaryAdvance advance : eligibleAdvances) {
            // Only advances belonging to the current employee
            if (!advance.getEmployee().getId().equals(runEmp.getEmployee().getId())) {
                continue;
            }
            // Repayment must have started
            if (advance.getRepaymentStartDate() == null ||
                advance.getRepaymentStartDate().isAfter(periodEnd)) {
                continue;
            }
            // Prevent duplicate deductions for the same payroll run
            if (salaryAdvanceRepaymentRepository.existsByAdvance_IdAndPayrollRun_Id(
                    advance.getId(), run.getId())) {
                continue;
            }

            // Determine deduction amount – never exceed outstanding balance
            BigDecimal installment = advance.getInstallmentAmount();
            if (installment == null) {
                installment = advance.getOutstandingBalance();
            }
            if (advance.getOutstandingBalance().compareTo(installment) < 0) {
                installment = advance.getOutstandingBalance();
            }

            // Create payroll line item for the deduction
            PayrollLineItem lineItem = PayrollLineItem.builder()
                    .payrollRunEmployee(runEmp)
                    .componentCode("SAL_ADV_DED")
                    .componentName("Salary Advance Repayment")
                    .type("DEDUCTION")
                    .amount(installment)
                    .build();
            lineItem.setTenantId(tenantId);
            lineItemRepository.save(lineItem);

            // Update employee totals for the repayment deduction
            runEmp.setTotalDeductions(runEmp.getTotalDeductions().add(installment));
            runEmp.setNetSalary(runEmp.getNetSalary().subtract(installment));
            // Persist the updated PayrollRunEmployee
            runEmployeeRepository.save(runEmp);

            // Create repayment record
            int installmentNumber = salaryAdvanceRepaymentRepository
                    .findByAdvance_IdAndTenantId(advance.getId(), tenantId)
                    .size() + 1;

            SalaryAdvanceRepayment repayment = SalaryAdvanceRepayment.builder()
                    .advance(advance)
                    .payrollRun(run)
                    .amount(installment)
                    .installmentNumber(installmentNumber)
                    .deductionDate(LocalDate.now())
                    .build();
            repayment.setTenantId(tenantId);
            salaryAdvanceRepaymentRepository.save(repayment);

            // Update advance balance and status
            advance.setOutstandingBalance(advance.getOutstandingBalance().subtract(installment));

            if (advance.getStatus() == SalaryAdvanceStatus.APPROVED) {
                advance.setStatus(SalaryAdvanceStatus.ACTIVE);
            }

            if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) == 0) {
                advance.setStatus(SalaryAdvanceStatus.FULLY_REPAID);
            } else if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0
                    && advance.getStatus() == SalaryAdvanceStatus.ACTIVE) {
                advance.setStatus(SalaryAdvanceStatus.PARTIALLY_REPAID);
            }

            salaryAdvanceRepository.save(advance);
        }
    }

    // =========================================================
    // APPROVAL
    // =========================================================
    // SALARY ADVANCE REPAYMENT INTEGRATION
    // =========================================================

    /**
     * Apply salary‑advance repayments for the given employee during a payroll run.
     * This method is called inside {@link #calculatePayrollRun(UUID, UUID)} for each
     * {@link PayrollRunEmployee}. It creates deduction line items, persists repayment
     * records and updates the advance status and outstanding balance.
     */
    private void applySalaryAdvanceRepayments(PayrollRunEmployee runEmp,
                                              PayrollRun run,
                                              UUID tenantId) {
        // Fetch eligible advances for the tenant
        List<SalaryAdvance> eligibleAdvances = salaryAdvanceRepository.findEligibleForPayrollRecovery(tenantId);

        // Payroll period end date determines when repayments start
        LocalDate periodEnd = run.getPayrollPeriod().getEndDate();

        for (SalaryAdvance advance : eligibleAdvances) {
            // Only advances belonging to the current employee
            if (!advance.getEmployee().getId().equals(runEmp.getEmployee().getId())) {
                continue;
            }
            // Repayment must have started
            if (advance.getRepaymentStartDate() == null ||
                advance.getRepaymentStartDate().isAfter(periodEnd)) {
                continue;
            }
            // Prevent duplicate deductions for the same payroll run
            if (salaryAdvanceRepaymentRepository.existsByAdvance_IdAndPayrollRun_Id(
                    advance.getId(), run.getId())) {
                continue;
            }

            // Determine deduction amount – never exceed outstanding balance
            BigDecimal installment = advance.getInstallmentAmount();
            if (installment == null) {
                installment = advance.getOutstandingBalance();
            }
            if (advance.getOutstandingBalance().compareTo(installment) < 0) {
                installment = advance.getOutstandingBalance();
            }

            // Create payroll line item for the deduction
            PayrollLineItem lineItem = PayrollLineItem.builder()
                    .payrollRunEmployee(runEmp)
                    .componentCode("SAL_ADV_DED")
                    .componentName("Salary Advance Repayment")
                    .type("DEDUCTION")
                    .amount(installment)
                    .build();
            lineItem.setTenantId(tenantId);
            lineItemRepository.save(lineItem);

            // Update employee totals for the repayment deduction
            runEmp.setTotalDeductions(runEmp.getTotalDeductions().add(installment));
            runEmp.setNetSalary(runEmp.getNetSalary().subtract(installment));
            // Persist the updated PayrollRunEmployee
            runEmployeeRepository.save(runEmp);

            // Create repayment record
            int installmentNumber = salaryAdvanceRepaymentRepository
                    .findByAdvance_IdAndTenantId(advance.getId(), tenantId)
                    .size() + 1;

            SalaryAdvanceRepayment repayment = SalaryAdvanceRepayment.builder()
                    .advance(advance)
                    .payrollRun(run)
                    .amount(installment)
                    .installmentNumber(installmentNumber)
                    .deductionDate(LocalDate.now())
                    .build();
            repayment.setTenantId(tenantId);
            salaryAdvanceRepaymentRepository.save(repayment);

            // Update advance balance and status
            advance.setOutstandingBalance(advance.getOutstandingBalance().subtract(installment));

            if (advance.getStatus() == SalaryAdvanceStatus.APPROVED) {
                advance.setStatus(SalaryAdvanceStatus.ACTIVE);
            }

            if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) == 0) {
                advance.setStatus(SalaryAdvanceStatus.FULLY_REPAID);
            } else if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0
                    && advance.getStatus() == SalaryAdvanceStatus.ACTIVE) {
                advance.setStatus(SalaryAdvanceStatus.PARTIALLY_REPAID);
            }

            salaryAdvanceRepository.save(advance);
        }
    }

    // =========================================================

    @Transactional
    public PayrollPeriodDto approvePeriod(UUID periodId) {

        UUID tenantId = getRequiredTenantId();

        PayrollPeriod period = periodRepository.findByIdAndTenantId(periodId, tenantId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Payroll period not found: " + periodId));

        if (!"REVIEW".equals(period.getStatus())) {
            throw new IllegalStateException(
                    "Period can only be approved from REVIEW status. Current: "
                            + period.getStatus());
        }

        // Fetch latest payroll run for this period
        List<PayrollRun> runs = runRepository.findByPayrollPeriodIdOrderByRunNumberDesc(period.getId());
        if (runs.isEmpty()) {
            throw new IllegalStateException("No payroll run found for period; cannot approve.");
        }
        PayrollRun latestRun = runs.get(0);
        List<PayrollRunEmployee> runEmployees = runEmployeeRepository.findByPayrollRunId(latestRun.getId());
        if (runEmployees.isEmpty()) {
            throw new IllegalStateException("Cannot approve payroll period: no employees were successfully calculated.");
        }

        period.setStatus("APPROVED");
        period = periodRepository.save(period);

        return toPeriodDto(period);
    }

    // =========================================================
    // SALARY ADVANCE REPAYMENT INTEGRATION
    // =========================================================

    /**
     * Apply salary‑advance repayments for the given employee during a payroll run.
     * This method is called inside {@link #calculatePayrollRun(UUID, UUID)} for each
     * {@link PayrollRunEmployee}. It creates deduction line items, persists repayment
     * records and updates the advance status and outstanding balance.
     */
    private void applySalaryAdvanceRepayments(PayrollRunEmployee runEmp,
                                              PayrollRun run,
                                              UUID tenantId) {
        // Fetch eligible advances for the tenant
        List<SalaryAdvance> eligibleAdvances = salaryAdvanceRepository.findEligibleForPayrollRecovery(tenantId);

        // Payroll period end date determines when repayments start
        LocalDate periodEnd = run.getPayrollPeriod().getEndDate();

        for (SalaryAdvance advance : eligibleAdvances) {
            // Only advances belonging to the current employee
            if (!advance.getEmployee().getId().equals(runEmp.getEmployee().getId())) {
                continue;
            }
            // Repayment must have started
            if (advance.getRepaymentStartDate() == null ||
                advance.getRepaymentStartDate().isAfter(periodEnd)) {
                continue;
            }
            // Prevent duplicate deductions for the same payroll run
            if (salaryAdvanceRepaymentRepository.existsByAdvance_IdAndPayrollRun_Id(
                    advance.getId(), run.getId())) {
                continue;
            }

            // Determine deduction amount – never exceed outstanding balance
            BigDecimal installment = advance.getInstallmentAmount();
            if (installment == null) {
                installment = advance.getOutstandingBalance();
            }
            if (advance.getOutstandingBalance().compareTo(installment) < 0) {
                installment = advance.getOutstandingBalance();
            }

            // Create payroll line item for the deduction
            PayrollLineItem lineItem = PayrollLineItem.builder()
                    .payrollRunEmployee(runEmp)
                    .componentCode("SAL_ADV_DED")
                    .componentName("Salary Advance Repayment")
                    .type("DEDUCTION")
                    .amount(installment)
                    .build();
            lineItem.setTenantId(tenantId);
            lineItemRepository.save(lineItem);

            // Update employee totals for the repayment deduction
            runEmp.setTotalDeductions(runEmp.getTotalDeductions().add(installment));
            runEmp.setNetSalary(runEmp.getNetSalary().subtract(installment));
            // Persist the updated PayrollRunEmployee
            runEmployeeRepository.save(runEmp);

            // Create repayment record
            int installmentNumber = salaryAdvanceRepaymentRepository
                    .findByAdvance_IdAndTenantId(advance.getId(), tenantId)
                    .size() + 1;

            SalaryAdvanceRepayment repayment = SalaryAdvanceRepayment.builder()
                    .advance(advance)
                    .payrollRun(run)
                    .amount(installment)
                    .installmentNumber(installmentNumber)
                    .deductionDate(LocalDate.now())
                    .build();
            repayment.setTenantId(tenantId);
            salaryAdvanceRepaymentRepository.save(repayment);

            // Update advance balance and status
            advance.setOutstandingBalance(advance.getOutstandingBalance().subtract(installment));

            if (advance.getStatus() == SalaryAdvanceStatus.APPROVED) {
                advance.setStatus(SalaryAdvanceStatus.ACTIVE);
            }

            if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) == 0) {
                advance.setStatus(SalaryAdvanceStatus.FULLY_REPAID);
            } else if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0
                    && advance.getStatus() == SalaryAdvanceStatus.ACTIVE) {
                advance.setStatus(SalaryAdvanceStatus.PARTIALLY_REPAID);
            }

            salaryAdvanceRepository.save(advance);
        }
    }

    // =========================================================
    // FINALIZATION AND PAYSLIP GENERATION
    // =========================================================
    // SALARY ADVANCE REPAYMENT INTEGRATION
    // =========================================================

    /**
     * Apply salary‑advance repayments for the given employee during a payroll run.
     * This method is called inside {@link #calculatePayrollRun(UUID, UUID)} for each
     * {@link PayrollRunEmployee}. It creates deduction line items, persists repayment
     * records and updates the advance status and outstanding balance.
     */
    private void applySalaryAdvanceRepayments(PayrollRunEmployee runEmp,
                                              PayrollRun run,
                                              UUID tenantId) {
        // Fetch eligible advances for the tenant
        List<SalaryAdvance> eligibleAdvances = salaryAdvanceRepository.findEligibleForPayrollRecovery(tenantId);

        // Payroll period end date determines when repayments start
        LocalDate periodEnd = run.getPayrollPeriod().getEndDate();

        for (SalaryAdvance advance : eligibleAdvances) {
            // Only advances belonging to the current employee
            if (!advance.getEmployee().getId().equals(runEmp.getEmployee().getId())) {
                continue;
            }
            // Repayment must have started
            if (advance.getRepaymentStartDate() == null ||
                advance.getRepaymentStartDate().isAfter(periodEnd)) {
                continue;
            }
            // Prevent duplicate deductions for the same payroll run
            if (salaryAdvanceRepaymentRepository.existsByAdvance_IdAndPayrollRun_Id(
                    advance.getId(), run.getId())) {
                continue;
            }

            // Determine deduction amount – never exceed outstanding balance
            BigDecimal installment = advance.getInstallmentAmount();
            if (installment == null) {
                installment = advance.getOutstandingBalance();
            }
            if (advance.getOutstandingBalance().compareTo(installment) < 0) {
                installment = advance.getOutstandingBalance();
            }

            // Create payroll line item for the deduction
            PayrollLineItem lineItem = PayrollLineItem.builder()
                    .payrollRunEmployee(runEmp)
                    .componentCode("SAL_ADV_DED")
                    .componentName("Salary Advance Repayment")
                    .type("DEDUCTION")
                    .amount(installment)
                    .build();
            lineItem.setTenantId(tenantId);
            lineItemRepository.save(lineItem);

            // Update employee totals for the repayment deduction
            runEmp.setTotalDeductions(runEmp.getTotalDeductions().add(installment));
            runEmp.setNetSalary(runEmp.getNetSalary().subtract(installment));
            // Persist the updated PayrollRunEmployee
            runEmployeeRepository.save(runEmp);

            // Create repayment record
            int installmentNumber = salaryAdvanceRepaymentRepository
                    .findByAdvance_IdAndTenantId(advance.getId(), tenantId)
                    .size() + 1;

            SalaryAdvanceRepayment repayment = SalaryAdvanceRepayment.builder()
                    .advance(advance)
                    .payrollRun(run)
                    .amount(installment)
                    .installmentNumber(installmentNumber)
                    .deductionDate(LocalDate.now())
                    .build();
            repayment.setTenantId(tenantId);
            salaryAdvanceRepaymentRepository.save(repayment);

            // Update advance balance and status
            advance.setOutstandingBalance(advance.getOutstandingBalance().subtract(installment));

            if (advance.getStatus() == SalaryAdvanceStatus.APPROVED) {
                advance.setStatus(SalaryAdvanceStatus.ACTIVE);
            }

            if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) == 0) {
                advance.setStatus(SalaryAdvanceStatus.FULLY_REPAID);
            } else if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0
                    && advance.getStatus() == SalaryAdvanceStatus.ACTIVE) {
                advance.setStatus(SalaryAdvanceStatus.PARTIALLY_REPAID);
            }

            salaryAdvanceRepository.save(advance);
        }
    }

    // =========================================================

    @Transactional
    public PayrollPeriodDto finalizePeriod(UUID periodId, UUID currentUserId) {

        UUID tenantId = getRequiredTenantId();

        PayrollPeriod period = periodRepository.findByIdAndTenantId(periodId, tenantId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Payroll period not found: " + periodId));

        if (!"APPROVED".equals(period.getStatus())) {
            throw new IllegalStateException(
                    "Period must be APPROVED before finalization. Current: "
                            + period.getStatus());
        }

        AppUser finalizer = currentUserId != null
                ? appUserRepository.findById(currentUserId).orElse(null)
                : null;

        period.setStatus("FINALIZED");
        period.setFinalizedBy(finalizer);
        period.setFinalizedAt(Instant.now());
        period = periodRepository.save(period);

        List<PayrollRun> runs =
                runRepository.findByPayrollPeriodIdOrderByRunNumberDesc(period.getId());

        if (!runs.isEmpty()) {

            PayrollRun latestRun = runs.get(0);

            List<PayrollRunEmployee> runEmps =
                    runEmployeeRepository.findByPayrollRunId(latestRun.getId());

            // Guard against finalizing a period where the latest run processed zero employees
            if (runEmps.isEmpty()) {
                throw new IllegalStateException("Cannot finalize payroll period: no employees were successfully calculated.");
            }

            for (PayrollRunEmployee re : runEmps) {

                re.setIsFrozen(true);
                runEmployeeRepository.save(re);

                if (payslipRepository.findByPayrollPeriodIdAndEmployeeId(
                        period.getId(), re.getEmployee().getId()).isEmpty()) {

                    String payslipNumber = String.format(
                            "PS-%d-%02d-%s",
                            period.getYear(),
                            period.getMonth(),
                            re.getEmployee().getEmployeeCode()
                    );

                    Map<String, Object> details = new HashMap<>();

                    details.put("employeeName",
                            re.getEmployee().getFirstName() + " "
                                    + re.getEmployee().getLastName());

                    details.put("employeeCode", re.getEmployee().getEmployeeCode());
                    details.put("period", period.getYear() + "-" + period.getMonth());
                    details.put("payableDays", re.getPayableDays());
                    details.put("gross", re.getGrossEarnings());
                    details.put("deductions", re.getTotalDeductions());
                    details.put("net", re.getNetSalary());

                    String json = "{}";

                    try {
                        json = objectMapper.writeValueAsString(details);
                    } catch (Exception ex) {
                        log.error("Could not serialize payslip data", ex);
                    }

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

        List<PayrollAdjustment> adjustments =
                adjustmentRepository.findByPayrollPeriodId(period.getId());

        for (PayrollAdjustment adj : adjustments) {
            adj.setIsProcessed(true);
            adjustmentRepository.save(adj);
        }

        return toPeriodDto(period);
    }

    // =========================================================
    // SALARY ADVANCE REPAYMENT INTEGRATION
    // =========================================================

    /**
     * Apply salary‑advance repayments for the given employee during a payroll run.
     * This method is called inside {@link #calculatePayrollRun(UUID, UUID)} for each
     * {@link PayrollRunEmployee}. It creates deduction line items, persists repayment
     * records and updates the advance status and outstanding balance.
     */
    private void applySalaryAdvanceRepayments(PayrollRunEmployee runEmp,
                                              PayrollRun run,
                                              UUID tenantId) {
        // Fetch eligible advances for the tenant
        List<SalaryAdvance> eligibleAdvances = salaryAdvanceRepository.findEligibleForPayrollRecovery(tenantId);

        // Payroll period end date determines when repayments start
        LocalDate periodEnd = run.getPayrollPeriod().getEndDate();

        for (SalaryAdvance advance : eligibleAdvances) {
            // Only advances belonging to the current employee
            if (!advance.getEmployee().getId().equals(runEmp.getEmployee().getId())) {
                continue;
            }
            // Repayment must have started
            if (advance.getRepaymentStartDate() == null ||
                advance.getRepaymentStartDate().isAfter(periodEnd)) {
                continue;
            }
            // Prevent duplicate deductions for the same payroll run
            if (salaryAdvanceRepaymentRepository.existsByAdvance_IdAndPayrollRun_Id(
                    advance.getId(), run.getId())) {
                continue;
            }

            // Determine deduction amount – never exceed outstanding balance
            BigDecimal installment = advance.getInstallmentAmount();
            if (installment == null) {
                installment = advance.getOutstandingBalance();
            }
            if (advance.getOutstandingBalance().compareTo(installment) < 0) {
                installment = advance.getOutstandingBalance();
            }

            // Create payroll line item for the deduction
            PayrollLineItem lineItem = PayrollLineItem.builder()
                    .payrollRunEmployee(runEmp)
                    .componentCode("SAL_ADV_DED")
                    .componentName("Salary Advance Repayment")
                    .type("DEDUCTION")
                    .amount(installment)
                    .build();
            lineItem.setTenantId(tenantId);
            lineItemRepository.save(lineItem);

            // Update employee totals for the repayment deduction
            runEmp.setTotalDeductions(runEmp.getTotalDeductions().add(installment));
            runEmp.setNetSalary(runEmp.getNetSalary().subtract(installment));
            // Persist the updated PayrollRunEmployee
            runEmployeeRepository.save(runEmp);

            // Create repayment record
            int installmentNumber = salaryAdvanceRepaymentRepository
                    .findByAdvance_IdAndTenantId(advance.getId(), tenantId)
                    .size() + 1;

            SalaryAdvanceRepayment repayment = SalaryAdvanceRepayment.builder()
                    .advance(advance)
                    .payrollRun(run)
                    .amount(installment)
                    .installmentNumber(installmentNumber)
                    .deductionDate(LocalDate.now())
                    .build();
            repayment.setTenantId(tenantId);
            salaryAdvanceRepaymentRepository.save(repayment);

            // Update advance balance and status
            advance.setOutstandingBalance(advance.getOutstandingBalance().subtract(installment));

            if (advance.getStatus() == SalaryAdvanceStatus.APPROVED) {
                advance.setStatus(SalaryAdvanceStatus.ACTIVE);
            }

            if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) == 0) {
                advance.setStatus(SalaryAdvanceStatus.FULLY_REPAID);
            } else if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0
                    && advance.getStatus() == SalaryAdvanceStatus.ACTIVE) {
                advance.setStatus(SalaryAdvanceStatus.PARTIALLY_REPAID);
            }

            salaryAdvanceRepository.save(advance);
        }
    }

    // =========================================================
    // PAYROLL ADJUSTMENTS
    // =========================================================
    // SALARY ADVANCE REPAYMENT INTEGRATION
    // =========================================================

    /**
     * Apply salary‑advance repayments for the given employee during a payroll run.
     * This method is called inside {@link #calculatePayrollRun(UUID, UUID)} for each
     * {@link PayrollRunEmployee}. It creates deduction line items, persists repayment
     * records and updates the advance status and outstanding balance.
     */
    private void applySalaryAdvanceRepayments(PayrollRunEmployee runEmp,
                                              PayrollRun run,
                                              UUID tenantId) {
        // Fetch eligible advances for the tenant
        List<SalaryAdvance> eligibleAdvances = salaryAdvanceRepository.findEligibleForPayrollRecovery(tenantId);

        // Payroll period end date determines when repayments start
        LocalDate periodEnd = run.getPayrollPeriod().getEndDate();

        for (SalaryAdvance advance : eligibleAdvances) {
            // Only advances belonging to the current employee
            if (!advance.getEmployee().getId().equals(runEmp.getEmployee().getId())) {
                continue;
            }
            // Repayment must have started
            if (advance.getRepaymentStartDate() == null ||
                advance.getRepaymentStartDate().isAfter(periodEnd)) {
                continue;
            }
            // Prevent duplicate deductions for the same payroll run
            if (salaryAdvanceRepaymentRepository.existsByAdvance_IdAndPayrollRun_Id(
                    advance.getId(), run.getId())) {
                continue;
            }

            // Determine deduction amount – never exceed outstanding balance
            BigDecimal installment = advance.getInstallmentAmount();
            if (installment == null) {
                installment = advance.getOutstandingBalance();
            }
            if (advance.getOutstandingBalance().compareTo(installment) < 0) {
                installment = advance.getOutstandingBalance();
            }

            // Create payroll line item for the deduction
            PayrollLineItem lineItem = PayrollLineItem.builder()
                    .payrollRunEmployee(runEmp)
                    .componentCode("SAL_ADV_DED")
                    .componentName("Salary Advance Repayment")
                    .type("DEDUCTION")
                    .amount(installment)
                    .build();
            lineItem.setTenantId(tenantId);
            lineItemRepository.save(lineItem);

            // Update employee totals for the repayment deduction
            runEmp.setTotalDeductions(runEmp.getTotalDeductions().add(installment));
            runEmp.setNetSalary(runEmp.getNetSalary().subtract(installment));
            // Persist the updated PayrollRunEmployee
            runEmployeeRepository.save(runEmp);

            // Create repayment record
            int installmentNumber = salaryAdvanceRepaymentRepository
                    .findByAdvance_IdAndTenantId(advance.getId(), tenantId)
                    .size() + 1;

            SalaryAdvanceRepayment repayment = SalaryAdvanceRepayment.builder()
                    .advance(advance)
                    .payrollRun(run)
                    .amount(installment)
                    .installmentNumber(installmentNumber)
                    .deductionDate(LocalDate.now())
                    .build();
            repayment.setTenantId(tenantId);
            salaryAdvanceRepaymentRepository.save(repayment);

            // Update advance balance and status
            advance.setOutstandingBalance(advance.getOutstandingBalance().subtract(installment));

            if (advance.getStatus() == SalaryAdvanceStatus.APPROVED) {
                advance.setStatus(SalaryAdvanceStatus.ACTIVE);
            }

            if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) == 0) {
                advance.setStatus(SalaryAdvanceStatus.FULLY_REPAID);
            } else if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0
                    && advance.getStatus() == SalaryAdvanceStatus.ACTIVE) {
                advance.setStatus(SalaryAdvanceStatus.PARTIALLY_REPAID);
            }

            salaryAdvanceRepository.save(advance);
        }
    }

    // =========================================================

    @Transactional
    public PayrollAdjustmentDto addAdjustment(
            PayrollAdjustmentDto dto, UUID currentUserId) {

        UUID tenantId = getRequiredTenantId();

        Employee employee = employeeRepository
                .findByIdAndTenantId(dto.getEmployeeId(), tenantId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Employee not found: " + dto.getEmployeeId()));

        if (dto.getIdempotencyKey() != null
                && adjustmentRepository.existsByTenantIdAndIdempotencyKey(
                        tenantId, dto.getIdempotencyKey())) {

            throw new IllegalArgumentException(
                    "Adjustment with idempotency key "
                            + dto.getIdempotencyKey() + " already recorded");
        }

        PayrollPeriod period = null;

        if (dto.getPayrollPeriodId() != null) {
            period = periodRepository.findByIdAndTenantId(
                    dto.getPayrollPeriodId(), tenantId).orElse(null);
        }

        AppUser authorizedBy = currentUserId != null
                ? appUserRepository.findById(currentUserId).orElse(null)
                : null;

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

    // =========================================================
    // SALARY ADVANCE REPAYMENT INTEGRATION
    // =========================================================

    /**
     * Apply salary‑advance repayments for the given employee during a payroll run.
     * This method is called inside {@link #calculatePayrollRun(UUID, UUID)} for each
     * {@link PayrollRunEmployee}. It creates deduction line items, persists repayment
     * records and updates the advance status and outstanding balance.
     */
    private void applySalaryAdvanceRepayments(PayrollRunEmployee runEmp,
                                              PayrollRun run,
                                              UUID tenantId) {
        // Fetch eligible advances for the tenant
        List<SalaryAdvance> eligibleAdvances = salaryAdvanceRepository.findEligibleForPayrollRecovery(tenantId);

        // Payroll period end date determines when repayments start
        LocalDate periodEnd = run.getPayrollPeriod().getEndDate();

        for (SalaryAdvance advance : eligibleAdvances) {
            // Only advances belonging to the current employee
            if (!advance.getEmployee().getId().equals(runEmp.getEmployee().getId())) {
                continue;
            }
            // Repayment must have started
            if (advance.getRepaymentStartDate() == null ||
                advance.getRepaymentStartDate().isAfter(periodEnd)) {
                continue;
            }
            // Prevent duplicate deductions for the same payroll run
            if (salaryAdvanceRepaymentRepository.existsByAdvance_IdAndPayrollRun_Id(
                    advance.getId(), run.getId())) {
                continue;
            }

            // Determine deduction amount – never exceed outstanding balance
            BigDecimal installment = advance.getInstallmentAmount();
            if (installment == null) {
                installment = advance.getOutstandingBalance();
            }
            if (advance.getOutstandingBalance().compareTo(installment) < 0) {
                installment = advance.getOutstandingBalance();
            }

            // Create payroll line item for the deduction
            PayrollLineItem lineItem = PayrollLineItem.builder()
                    .payrollRunEmployee(runEmp)
                    .componentCode("SAL_ADV_DED")
                    .componentName("Salary Advance Repayment")
                    .type("DEDUCTION")
                    .amount(installment)
                    .build();
            lineItem.setTenantId(tenantId);
            lineItemRepository.save(lineItem);

            // Update employee totals for the repayment deduction
            runEmp.setTotalDeductions(runEmp.getTotalDeductions().add(installment));
            runEmp.setNetSalary(runEmp.getNetSalary().subtract(installment));
            // Persist the updated PayrollRunEmployee
            runEmployeeRepository.save(runEmp);

            // Create repayment record
            int installmentNumber = salaryAdvanceRepaymentRepository
                    .findByAdvance_IdAndTenantId(advance.getId(), tenantId)
                    .size() + 1;

            SalaryAdvanceRepayment repayment = SalaryAdvanceRepayment.builder()
                    .advance(advance)
                    .payrollRun(run)
                    .amount(installment)
                    .installmentNumber(installmentNumber)
                    .deductionDate(LocalDate.now())
                    .build();
            repayment.setTenantId(tenantId);
            salaryAdvanceRepaymentRepository.save(repayment);

            // Update advance balance and status
            advance.setOutstandingBalance(advance.getOutstandingBalance().subtract(installment));

            if (advance.getStatus() == SalaryAdvanceStatus.APPROVED) {
                advance.setStatus(SalaryAdvanceStatus.ACTIVE);
            }

            if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) == 0) {
                advance.setStatus(SalaryAdvanceStatus.FULLY_REPAID);
            } else if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0
                    && advance.getStatus() == SalaryAdvanceStatus.ACTIVE) {
                advance.setStatus(SalaryAdvanceStatus.PARTIALLY_REPAID);
            }

            salaryAdvanceRepository.save(advance);
        }
    }

    // =========================================================
    // PAYSLIPS
    // =========================================================
    // SALARY ADVANCE REPAYMENT INTEGRATION
    // =========================================================

    /**
     * Apply salary‑advance repayments for the given employee during a payroll run.
     * This method is called inside {@link #calculatePayrollRun(UUID, UUID)} for each
     * {@link PayrollRunEmployee}. It creates deduction line items, persists repayment
     * records and updates the advance status and outstanding balance.
     */
    private void applySalaryAdvanceRepayments(PayrollRunEmployee runEmp,
                                              PayrollRun run,
                                              UUID tenantId) {
        // Fetch eligible advances for the tenant
        List<SalaryAdvance> eligibleAdvances = salaryAdvanceRepository.findEligibleForPayrollRecovery(tenantId);

        // Payroll period end date determines when repayments start
        LocalDate periodEnd = run.getPayrollPeriod().getEndDate();

        for (SalaryAdvance advance : eligibleAdvances) {
            // Only advances belonging to the current employee
            if (!advance.getEmployee().getId().equals(runEmp.getEmployee().getId())) {
                continue;
            }
            // Repayment must have started
            if (advance.getRepaymentStartDate() == null ||
                advance.getRepaymentStartDate().isAfter(periodEnd)) {
                continue;
            }
            // Prevent duplicate deductions for the same payroll run
            if (salaryAdvanceRepaymentRepository.existsByAdvance_IdAndPayrollRun_Id(
                    advance.getId(), run.getId())) {
                continue;
            }

            // Determine deduction amount – never exceed outstanding balance
            BigDecimal installment = advance.getInstallmentAmount();
            if (installment == null) {
                installment = advance.getOutstandingBalance();
            }
            if (advance.getOutstandingBalance().compareTo(installment) < 0) {
                installment = advance.getOutstandingBalance();
            }

            // Create payroll line item for the deduction
            PayrollLineItem lineItem = PayrollLineItem.builder()
                    .payrollRunEmployee(runEmp)
                    .componentCode("SAL_ADV_DED")
                    .componentName("Salary Advance Repayment")
                    .type("DEDUCTION")
                    .amount(installment)
                    .build();
            lineItem.setTenantId(tenantId);
            lineItemRepository.save(lineItem);

            // Update employee totals for the repayment deduction
            runEmp.setTotalDeductions(runEmp.getTotalDeductions().add(installment));
            runEmp.setNetSalary(runEmp.getNetSalary().subtract(installment));
            // Persist the updated PayrollRunEmployee
            runEmployeeRepository.save(runEmp);

            // Create repayment record
            int installmentNumber = salaryAdvanceRepaymentRepository
                    .findByAdvance_IdAndTenantId(advance.getId(), tenantId)
                    .size() + 1;

            SalaryAdvanceRepayment repayment = SalaryAdvanceRepayment.builder()
                    .advance(advance)
                    .payrollRun(run)
                    .amount(installment)
                    .installmentNumber(installmentNumber)
                    .deductionDate(LocalDate.now())
                    .build();
            repayment.setTenantId(tenantId);
            salaryAdvanceRepaymentRepository.save(repayment);

            // Update advance balance and status
            advance.setOutstandingBalance(advance.getOutstandingBalance().subtract(installment));

            if (advance.getStatus() == SalaryAdvanceStatus.APPROVED) {
                advance.setStatus(SalaryAdvanceStatus.ACTIVE);
            }

            if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) == 0) {
                advance.setStatus(SalaryAdvanceStatus.FULLY_REPAID);
            } else if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0
                    && advance.getStatus() == SalaryAdvanceStatus.ACTIVE) {
                advance.setStatus(SalaryAdvanceStatus.PARTIALLY_REPAID);
            }

            salaryAdvanceRepository.save(advance);
        }
    }

    // =========================================================

    @Transactional(readOnly = true)
    public List<PayslipDto> getEmployeePayslips(UUID employeeId) {

        UUID tenantId = getRequiredTenantId();

        if (employeeRepository.findByIdAndTenantId(employeeId, tenantId).isEmpty()) {
            throw new ResourceNotFoundException(
                    "Employee not found for current tenant: " + employeeId);
        }

        return payslipRepository
                .findByTenantIdAndEmployeeIdOrderByIssueDateDesc(tenantId, employeeId)
                .stream()
                .map(this::toPayslipDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PayslipDto getPayslipById(UUID payslipId) {

        UUID tenantId = getRequiredTenantId();

        Payslip payslip = payslipRepository.findByIdAndTenantId(payslipId, tenantId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Payslip not found: " + payslipId));

        return toPayslipDto(payslip);
    }

    // =========================================================
    // SALARY ADVANCE REPAYMENT INTEGRATION
    // =========================================================

    /**
     * Apply salary‑advance repayments for the given employee during a payroll run.
     * This method is called inside {@link #calculatePayrollRun(UUID, UUID)} for each
     * {@link PayrollRunEmployee}. It creates deduction line items, persists repayment
     * records and updates the advance status and outstanding balance.
     */
    private void applySalaryAdvanceRepayments(PayrollRunEmployee runEmp,
                                              PayrollRun run,
                                              UUID tenantId) {
        // Fetch eligible advances for the tenant
        List<SalaryAdvance> eligibleAdvances = salaryAdvanceRepository.findEligibleForPayrollRecovery(tenantId);

        // Payroll period end date determines when repayments start
        LocalDate periodEnd = run.getPayrollPeriod().getEndDate();

        for (SalaryAdvance advance : eligibleAdvances) {
            // Only advances belonging to the current employee
            if (!advance.getEmployee().getId().equals(runEmp.getEmployee().getId())) {
                continue;
            }
            // Repayment must have started
            if (advance.getRepaymentStartDate() == null ||
                advance.getRepaymentStartDate().isAfter(periodEnd)) {
                continue;
            }
            // Prevent duplicate deductions for the same payroll run
            if (salaryAdvanceRepaymentRepository.existsByAdvance_IdAndPayrollRun_Id(
                    advance.getId(), run.getId())) {
                continue;
            }

            // Determine deduction amount – never exceed outstanding balance
            BigDecimal installment = advance.getInstallmentAmount();
            if (installment == null) {
                installment = advance.getOutstandingBalance();
            }
            if (advance.getOutstandingBalance().compareTo(installment) < 0) {
                installment = advance.getOutstandingBalance();
            }

            // Create payroll line item for the deduction
            PayrollLineItem lineItem = PayrollLineItem.builder()
                    .payrollRunEmployee(runEmp)
                    .componentCode("SAL_ADV_DED")
                    .componentName("Salary Advance Repayment")
                    .type("DEDUCTION")
                    .amount(installment)
                    .build();
            lineItem.setTenantId(tenantId);
            lineItemRepository.save(lineItem);

            // Update employee totals for the repayment deduction
            runEmp.setTotalDeductions(runEmp.getTotalDeductions().add(installment));
            runEmp.setNetSalary(runEmp.getNetSalary().subtract(installment));
            // Persist the updated PayrollRunEmployee
            runEmployeeRepository.save(runEmp);

            // Create repayment record
            int installmentNumber = salaryAdvanceRepaymentRepository
                    .findByAdvance_IdAndTenantId(advance.getId(), tenantId)
                    .size() + 1;

            SalaryAdvanceRepayment repayment = SalaryAdvanceRepayment.builder()
                    .advance(advance)
                    .payrollRun(run)
                    .amount(installment)
                    .installmentNumber(installmentNumber)
                    .deductionDate(LocalDate.now())
                    .build();
            repayment.setTenantId(tenantId);
            salaryAdvanceRepaymentRepository.save(repayment);

            // Update advance balance and status
            advance.setOutstandingBalance(advance.getOutstandingBalance().subtract(installment));

            if (advance.getStatus() == SalaryAdvanceStatus.APPROVED) {
                advance.setStatus(SalaryAdvanceStatus.ACTIVE);
            }

            if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) == 0) {
                advance.setStatus(SalaryAdvanceStatus.FULLY_REPAID);
            } else if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0
                    && advance.getStatus() == SalaryAdvanceStatus.ACTIVE) {
                advance.setStatus(SalaryAdvanceStatus.PARTIALLY_REPAID);
            }

            salaryAdvanceRepository.save(advance);
        }
    }

    // =========================================================
    // DTO MAPPERS
    // =========================================================
    // SALARY ADVANCE REPAYMENT INTEGRATION
    // =========================================================

    /**
     * Apply salary‑advance repayments for the given employee during a payroll run.
     * This method is called inside {@link #calculatePayrollRun(UUID, UUID)} for each
     * {@link PayrollRunEmployee}. It creates deduction line items, persists repayment
     * records and updates the advance status and outstanding balance.
     */
    private void applySalaryAdvanceRepayments(PayrollRunEmployee runEmp,
                                              PayrollRun run,
                                              UUID tenantId) {
        // Fetch eligible advances for the tenant
        List<SalaryAdvance> eligibleAdvances = salaryAdvanceRepository.findEligibleForPayrollRecovery(tenantId);

        // Payroll period end date determines when repayments start
        LocalDate periodEnd = run.getPayrollPeriod().getEndDate();

        for (SalaryAdvance advance : eligibleAdvances) {
            // Only advances belonging to the current employee
            if (!advance.getEmployee().getId().equals(runEmp.getEmployee().getId())) {
                continue;
            }
            // Repayment must have started
            if (advance.getRepaymentStartDate() == null ||
                advance.getRepaymentStartDate().isAfter(periodEnd)) {
                continue;
            }
            // Prevent duplicate deductions for the same payroll run
            if (salaryAdvanceRepaymentRepository.existsByAdvance_IdAndPayrollRun_Id(
                    advance.getId(), run.getId())) {
                continue;
            }

            // Determine deduction amount – never exceed outstanding balance
            BigDecimal installment = advance.getInstallmentAmount();
            if (installment == null) {
                installment = advance.getOutstandingBalance();
            }
            if (advance.getOutstandingBalance().compareTo(installment) < 0) {
                installment = advance.getOutstandingBalance();
            }

            // Create payroll line item for the deduction
            PayrollLineItem lineItem = PayrollLineItem.builder()
                    .payrollRunEmployee(runEmp)
                    .componentCode("SAL_ADV_DED")
                    .componentName("Salary Advance Repayment")
                    .type("DEDUCTION")
                    .amount(installment)
                    .build();
            lineItem.setTenantId(tenantId);
            lineItemRepository.save(lineItem);

            // Update employee totals for the repayment deduction
            runEmp.setTotalDeductions(runEmp.getTotalDeductions().add(installment));
            runEmp.setNetSalary(runEmp.getNetSalary().subtract(installment));
            // Persist the updated PayrollRunEmployee
            runEmployeeRepository.save(runEmp);

            // Create repayment record
            int installmentNumber = salaryAdvanceRepaymentRepository
                    .findByAdvance_IdAndTenantId(advance.getId(), tenantId)
                    .size() + 1;

            SalaryAdvanceRepayment repayment = SalaryAdvanceRepayment.builder()
                    .advance(advance)
                    .payrollRun(run)
                    .amount(installment)
                    .installmentNumber(installmentNumber)
                    .deductionDate(LocalDate.now())
                    .build();
            repayment.setTenantId(tenantId);
            salaryAdvanceRepaymentRepository.save(repayment);

            // Update advance balance and status
            advance.setOutstandingBalance(advance.getOutstandingBalance().subtract(installment));

            if (advance.getStatus() == SalaryAdvanceStatus.APPROVED) {
                advance.setStatus(SalaryAdvanceStatus.ACTIVE);
            }

            if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) == 0) {
                advance.setStatus(SalaryAdvanceStatus.FULLY_REPAID);
            } else if (advance.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0
                    && advance.getStatus() == SalaryAdvanceStatus.ACTIVE) {
                advance.setStatus(SalaryAdvanceStatus.PARTIALLY_REPAID);
            }

            salaryAdvanceRepository.save(advance);
        }
    }

    // =========================================================

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
                .finalizedBy(p.getFinalizedBy() != null
                        ? p.getFinalizedBy().getId() : null)
                .finalizedAt(p.getFinalizedAt())
                .build();
    }

    private PayrollAdjustmentDto toAdjustmentDto(PayrollAdjustment a) {

        return PayrollAdjustmentDto.builder()
                .id(a.getId())
                .employeeId(a.getEmployee().getId())
                .employeeName(a.getEmployee().getFirstName()
                        + " " + a.getEmployee().getLastName())
                .payrollPeriodId(a.getPayrollPeriod() != null
                        ? a.getPayrollPeriod().getId() : null)
                .adjustmentType(a.getAdjustmentType())
                .amount(a.getAmount())
                .reason(a.getReason())
                .isProcessed(a.getIsProcessed())
                .idempotencyKey(a.getIdempotencyKey())
                .authorizedBy(a.getAuthorizedBy() != null
                        ? a.getAuthorizedBy().getId() : null)
                .build();
    }

    private PayslipDto toPayslipDto(Payslip p) {

        return PayslipDto.builder()
                .id(p.getId())
                .employeeId(p.getEmployee().getId())
                .employeeCode(p.getEmployee().getEmployeeCode())
                .employeeName(p.getEmployee().getFirstName()
                        + " " + p.getEmployee().getLastName())
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

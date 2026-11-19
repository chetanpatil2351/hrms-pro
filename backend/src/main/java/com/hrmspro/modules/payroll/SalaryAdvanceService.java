package com.hrmspro.modules.payroll;

import com.hrmspro.common.context.TenantContext;
import com.hrmspro.common.exception.ResourceNotFoundException;
import com.hrmspro.config.security.UserPrincipal;
import com.hrmspro.config.security.UserPrincipalHelper;
import com.hrmspro.modules.employee.Employee;
import com.hrmspro.modules.employee.EmployeeRepository;
import com.hrmspro.modules.payroll.dto.SalaryAdvanceRequestDto;
import com.hrmspro.modules.payroll.dto.SalaryAdvanceResponseDto;
import com.hrmspro.modules.payroll.entity.SalaryAdvance;
import com.hrmspro.modules.payroll.entity.SalaryAdvanceStatus;
import com.hrmspro.modules.payroll.entity.SalaryAdvanceRepayment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service handling salary advance operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalaryAdvanceService {

    private final SalaryAdvanceRepository salaryAdvanceRepository;
    private final EmployeeRepository employeeRepository;
    private final UserPrincipalHelper userPrincipalHelper;
    private final SalaryAdvanceRepaymentRepository repaymentRepository;

    /**
     * Create a new salary advance request for the authenticated employee.
     */
    @Transactional
    public SalaryAdvanceResponseDto createAdvanceRequest(SalaryAdvanceRequestDto request) {
        Object principalObj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principalObj instanceof UserPrincipal)) {
            throw new IllegalStateException("Authenticated principal is not of type UserPrincipal");
        }
        UserPrincipal principal = (UserPrincipal) principalObj;
        var appUser = userPrincipalHelper.getAppUser(principal);
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context not set");
        }
        Employee employee = employeeRepository.findByTenantIdAndUserId(tenantId, appUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found for current user"));
        if (request.getRequestedAmount() == null || request.getRequestedAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Requested amount must be positive");
        }
        SalaryAdvance advance = SalaryAdvance.builder()
                .employee(employee)
                .requestedAmount(request.getRequestedAmount())
                .notes(request.getNotes())
                .installmentCount(request.getInstallmentCount())
                .status(SalaryAdvanceStatus.REQUESTED)
                .requestDate(Instant.now())
                .approvedAmount(BigDecimal.ZERO)
                .installmentAmount(BigDecimal.ZERO)
                .outstandingBalance(BigDecimal.ZERO)
                .build();
        advance.setTenantId(tenantId);
        SalaryAdvance saved = salaryAdvanceRepository.save(advance);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<SalaryAdvanceResponseDto> getMyAdvances(UserPrincipal principal) {
        UUID tenantId = TenantContext.getTenantId();
        var appUser = userPrincipalHelper.getAppUser(principal);
        var employee = employeeRepository.findByTenantIdAndUserId(tenantId, appUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found for current user"));
        return salaryAdvanceRepository.findByEmployee_IdAndTenantId(employee.getId(), tenantId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public SalaryAdvanceResponseDto getAdvanceForEmployee(UUID advanceId, UserPrincipal principal) {
        UUID tenantId = TenantContext.getTenantId();
        var appUser = userPrincipalHelper.getAppUser(principal);
        var employee = employeeRepository.findByTenantIdAndUserId(tenantId, appUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found for current user"));
        var advance = salaryAdvanceRepository.findByIdAndTenantId(advanceId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Salary advance not found"));
        if (!advance.getEmployee().getId().equals(employee.getId())) {
            throw new AccessDeniedException("Cannot access another employee's salary advance");
        }
        return toDto(advance);
    }

    @Transactional(readOnly = true)
    public List<SalaryAdvanceRepayment> getRepaymentsForEmployee(UUID advanceId, UserPrincipal principal) {
        getAdvanceForEmployee(advanceId, principal);
        UUID tenantId = TenantContext.getTenantId();
        return repaymentRepository.findByAdvance_IdAndTenantId(advanceId, tenantId);
    }

    @Transactional(readOnly = true)
    public List<SalaryAdvanceResponseDto> listAllForTenant(UUID tenantId) {
        return salaryAdvanceRepository.findByTenantId(tenantId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public SalaryAdvanceResponseDto approveAdvance(UUID advanceId, UserPrincipal principal) {
        UUID tenantId = TenantContext.getTenantId();
        var advance = salaryAdvanceRepository.findByIdAndTenantId(advanceId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Salary advance not found"));
        if (!SalaryAdvanceStatus.REQUESTED.equals(advance.getStatus())) {
            throw new IllegalStateException("Only REQUESTED advances can be approved");
        }
        advance.setApprovedAmount(advance.getRequestedAmount());
        int count = (advance.getInstallmentCount() != null && advance.getInstallmentCount() > 0)
                ? advance.getInstallmentCount()
                : 1;
        advance.setInstallmentCount(count);
        advance.setInstallmentAmount(advance.getApprovedAmount()
                .divide(BigDecimal.valueOf(count), java.math.RoundingMode.HALF_UP));
        advance.setOutstandingBalance(advance.getApprovedAmount());
        advance.setRepaymentStartDate(LocalDate.now());
        var approver = userPrincipalHelper.getAppUser(principal);
        advance.setApprovedBy(approver);
        advance.setDecisionDate(Instant.now());
        advance.setStatus(SalaryAdvanceStatus.APPROVED);
        salaryAdvanceRepository.save(advance);
        return toDto(advance);
    }

    @Transactional
    public SalaryAdvanceResponseDto rejectAdvance(UUID advanceId, UserPrincipal principal) {
        UUID tenantId = TenantContext.getTenantId();
        var advance = salaryAdvanceRepository.findByIdAndTenantId(advanceId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Salary advance not found"));
        if (!SalaryAdvanceStatus.REQUESTED.equals(advance.getStatus())) {
            throw new IllegalStateException("Only REQUESTED advances can be rejected");
        }
        advance.setApprovedAmount(BigDecimal.ZERO);
        advance.setInstallmentAmount(BigDecimal.ZERO);
        advance.setOutstandingBalance(BigDecimal.ZERO);
        advance.setRepaymentStartDate(null);
        advance.setApprovedBy(null);
        advance.setDecisionDate(Instant.now());
        advance.setStatus(SalaryAdvanceStatus.REJECTED);
        salaryAdvanceRepository.save(advance);
        return toDto(advance);
    }

    private SalaryAdvanceResponseDto toDto(SalaryAdvance adv) {
        SalaryAdvanceResponseDto dto = new SalaryAdvanceResponseDto();
        dto.setId(adv.getId());
        dto.setEmployeeId(adv.getEmployee().getId());
        dto.setRequestedAmount(adv.getRequestedAmount());
        dto.setApprovedAmount(adv.getApprovedAmount());
        dto.setStatus(adv.getStatus());
        dto.setRequestDate(adv.getRequestDate());
        dto.setDecisionDate(adv.getDecisionDate());
        dto.setRepaymentStartDate(adv.getRepaymentStartDate());
        dto.setInstallmentCount(adv.getInstallmentCount());
        dto.setInstallmentAmount(adv.getInstallmentAmount());
        dto.setOutstandingBalance(adv.getOutstandingBalance());
        dto.setNotes(adv.getNotes());
        if (adv.getApprovedBy() != null) {
            dto.setApproverId(adv.getApprovedBy().getId());
        }
        return dto;
    }
}

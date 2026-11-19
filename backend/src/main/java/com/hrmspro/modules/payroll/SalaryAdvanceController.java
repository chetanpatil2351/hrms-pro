package com.hrmspro.modules.payroll;

import com.hrmspro.common.response.ApiResponse;
import com.hrmspro.config.security.UserPrincipal;
import com.hrmspro.modules.payroll.dto.SalaryAdvanceRequestDto;
import com.hrmspro.modules.payroll.dto.SalaryAdvanceResponseDto;
import com.hrmspro.modules.payroll.entity.SalaryAdvanceRepayment;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for Salary Advance operations.
 * Base path: /api/v1/payroll/salary-advances
 */
@RestController
@RequestMapping("/api/v1/payroll/salary-advances")
@RequiredArgsConstructor
public class SalaryAdvanceController {

    private final SalaryAdvanceService salaryAdvanceService;

    // ------- Employee endpoints -------

    /**
     * Submit a new salary advance request (employee only).
     */
    @PostMapping
    @PreAuthorize("hasAuthority('EMPLOYEE')")
    public ResponseEntity<ApiResponse<SalaryAdvanceResponseDto>> submitAdvance(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SalaryAdvanceRequestDto request) {
        SalaryAdvanceResponseDto created = salaryAdvanceService.createAdvanceRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Salary advance request submitted"));
    }

    /**
     * List the authenticated employee's own salary advances.
     */
    @GetMapping("/my")
    @PreAuthorize("hasAuthority('EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<SalaryAdvanceResponseDto>>> getMyAdvances(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<SalaryAdvanceResponseDto> list = salaryAdvanceService.getMyAdvances(principal);
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    /**
     * Get a specific advance belonging to the authenticated employee.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYEE')")
    public ResponseEntity<ApiResponse<SalaryAdvanceResponseDto>> getMyAdvance(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        SalaryAdvanceResponseDto dto = salaryAdvanceService.getAdvanceForEmployee(id, principal);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    /**
     * Get repayment history for a specific advance of the authenticated employee.
     */
    @GetMapping("/{id}/repayments")
    @PreAuthorize("hasAuthority('EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<SalaryAdvanceRepayment>>> getMyRepayments(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<SalaryAdvanceRepayment> repayments = salaryAdvanceService.getRepaymentsForEmployee(id, principal);
        return ResponseEntity.ok(ApiResponse.success(repayments));
    }

    // ------- HR/Admin endpoints (tenant scoped) -------

    /**
     * List all salary advances for the tenant (HR/Admin).
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<SalaryAdvanceResponseDto>>> listAllForTenant(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<SalaryAdvanceResponseDto> list = salaryAdvanceService.listAllForTenant(principal.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    /**
     * Approve a salary advance request (HR/Admin).
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('HR_MANAGER')")
    public ResponseEntity<ApiResponse<SalaryAdvanceResponseDto>> approveAdvance(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        SalaryAdvanceResponseDto updated = salaryAdvanceService.approveAdvance(id, principal);
        return ResponseEntity.ok(ApiResponse.success(updated, "Salary advance approved"));
    }

    /**
     * Reject a salary advance request (HR/Admin).
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('HR_MANAGER')")
    public ResponseEntity<ApiResponse<SalaryAdvanceResponseDto>> rejectAdvance(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        SalaryAdvanceResponseDto updated = salaryAdvanceService.rejectAdvance(id, principal);
        return ResponseEntity.ok(ApiResponse.success(updated, "Salary advance rejected"));
    }
}

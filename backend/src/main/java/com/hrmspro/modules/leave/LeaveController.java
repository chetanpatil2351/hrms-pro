package com.hrmspro.modules.leave;

import com.hrmspro.common.context.TenantContext;
import com.hrmspro.common.response.ApiResponse;
import com.hrmspro.config.security.UserPrincipal;
import com.hrmspro.modules.leave.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leaves")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    // --- Leave Types ---

    @PostMapping("/types")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LeaveTypeDto>> createLeaveType(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody LeaveTypeDto request) {
        UUID tenantId = TenantContext.getTenantId();
        LeaveTypeDto response = leaveService.createLeaveType(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Leave type created"));
    }

    @GetMapping("/types")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'BRANCH_MANAGER', 'EMPLOYEE', 'ATTENDANCE_MANAGER', 'PAYROLL_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<LeaveTypeDto>>> getActiveLeaveTypes(
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID tenantId = TenantContext.getTenantId();
        List<LeaveTypeDto> list = leaveService.getActiveLeaveTypes(tenantId);
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    // --- Leave Policies ---

    @PostMapping("/policies")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LeavePolicyDto>> configurePolicy(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody LeavePolicyDto request) {
        UUID tenantId = TenantContext.getTenantId();
        LeavePolicyDto response = leaveService.configurePolicy(tenantId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Leave policy configured"));
    }

    // --- Leave Applications ---

    @PostMapping("/apply")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'COMPANY_ADMIN', 'HR_MANAGER', 'BRANCH_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LeaveApplicationDto>> applyLeave(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody ApplyLeaveRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        LeaveApplicationDto response = leaveService.applyLeave(tenantId, principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Leave application submitted"));
    }

    @GetMapping("/my-requests")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'COMPANY_ADMIN', 'HR_MANAGER', 'BRANCH_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<LeaveApplicationDto>>> getMyRequests(
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID tenantId = TenantContext.getTenantId();
        List<LeaveApplicationDto> list = leaveService.getMyRequests(tenantId, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/my-balance")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'COMPANY_ADMIN', 'HR_MANAGER', 'BRANCH_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<LeaveBalanceDto>>> getMyBalance(
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID tenantId = TenantContext.getTenantId();
        List<LeaveBalanceDto> list = leaveService.getMyBalances(tenantId, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'COMPANY_ADMIN', 'HR_MANAGER', 'BRANCH_MANAGER', 'ATTENDANCE_MANAGER', 'PAYROLL_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LeaveApplicationDto>> getApplicationById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        LeaveApplicationDto response = leaveService.getApplicationById(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'COMPANY_ADMIN', 'HR_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LeaveApplicationDto>> cancelLeave(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        LeaveApplicationDto response = leaveService.cancelLeave(tenantId, principal.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success(response, "Leave application cancelled"));
    }

    // --- Approval Workflow ---

    @GetMapping("/approvals/pending")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'BRANCH_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<LeaveApplicationDto>>> getPendingApprovals(
            @AuthenticationPrincipal UserPrincipal principal,
            Pageable pageable) {
        UUID tenantId = TenantContext.getTenantId();
        Page<LeaveApplicationDto> page = leaveService.getPendingApprovals(tenantId, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'BRANCH_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LeaveApplicationDto>> approveLeave(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody(required = false) LeaveReviewRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        String comments = request != null ? request.getComments() : "Approved";
        LeaveApplicationDto response = leaveService.approveLeave(tenantId, principal.getUserId(), id, comments);
        return ResponseEntity.ok(ApiResponse.success(response, "Leave application approved"));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'BRANCH_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LeaveApplicationDto>> rejectLeave(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody(required = false) LeaveReviewRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        String comments = request != null ? request.getComments() : "Rejected";
        LeaveApplicationDto response = leaveService.rejectLeave(tenantId, principal.getUserId(), id, comments);
        return ResponseEntity.ok(ApiResponse.success(response, "Leave application rejected"));
    }

    // --- Manual Adjustment ---

    @PostMapping("/adjustments")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<LeaveBalanceDto>> manualAdjustment(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody ManualAdjustmentRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        LeaveBalanceDto response = leaveService.manualAdjustment(tenantId, principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(response, "Leave balance adjusted successfully"));
    }

    // --- Reports ---

    @GetMapping("/reports/employee/{employeeId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'BRANCH_MANAGER', 'PAYROLL_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<LeaveApplicationDto>>> getEmployeeReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            Pageable pageable) {
        UUID tenantId = TenantContext.getTenantId();
        Page<LeaveApplicationDto> page = leaveService.getLeaveReport(tenantId, employeeId, null, null, null, null, start, end, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/reports/branch/{branchId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'BRANCH_MANAGER', 'PAYROLL_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<LeaveApplicationDto>>> getBranchReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            Pageable pageable) {
        UUID tenantId = TenantContext.getTenantId();
        Page<LeaveApplicationDto> page = leaveService.getLeaveReport(tenantId, null, branchId, null, null, null, start, end, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/reports/department/{departmentId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'BRANCH_MANAGER', 'PAYROLL_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<LeaveApplicationDto>>> getDepartmentReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID departmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            Pageable pageable) {
        UUID tenantId = TenantContext.getTenantId();
        Page<LeaveApplicationDto> page = leaveService.getLeaveReport(tenantId, null, null, departmentId, null, null, start, end, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/reports/summary")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'BRANCH_MANAGER', 'PAYROLL_MANAGER', 'ACCOUNTANT', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<LeaveApplicationDto>>> getSummaryReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID leaveTypeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            Pageable pageable) {
        UUID tenantId = TenantContext.getTenantId();
        Page<LeaveApplicationDto> page = leaveService.getLeaveReport(tenantId, null, branchId, departmentId, leaveTypeId, status, start, end, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }
}

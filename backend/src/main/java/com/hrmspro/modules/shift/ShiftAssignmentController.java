package com.hrmspro.modules.shift;

import com.hrmspro.common.context.TenantContext;
import com.hrmspro.common.response.ApiResponse;
import com.hrmspro.config.security.UserPrincipal;
import com.hrmspro.modules.shift.dto.BulkShiftAssignmentRequest;
import com.hrmspro.modules.shift.dto.ShiftAssignmentRequest;
import com.hrmspro.modules.shift.dto.ShiftAssignmentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shifts/assignments")
@RequiredArgsConstructor
public class ShiftAssignmentController {

    private final ShiftAssignmentService assignmentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'BRANCH_MANAGER', 'ATTENDANCE_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ShiftAssignmentResponse>> assignShift(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody ShiftAssignmentRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        ShiftAssignmentResponse response = assignmentService.assignShift(tenantId, principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Shift assigned successfully"));
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bulkAssignShift(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody BulkShiftAssignmentRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        Map<String, Object> result = assignmentService.bulkAssignShift(tenantId, principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(result, "Bulk assignment completed"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'BRANCH_MANAGER', 'ATTENDANCE_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<ShiftAssignmentResponse>>> getAssignments(
            @AuthenticationPrincipal UserPrincipal principal,
            Pageable pageable) {
        UUID tenantId = TenantContext.getTenantId();
        Page<ShiftAssignmentResponse> page = assignmentService.getAssignments(tenantId, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'BRANCH_MANAGER', 'ATTENDANCE_MANAGER', 'EMPLOYEE', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<ShiftAssignmentResponse>>> getAssignmentsForEmployee(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID employeeId) {
        UUID tenantId = TenantContext.getTenantId();
        List<ShiftAssignmentResponse> list = assignmentService.getAssignmentsForEmployee(tenantId, employeeId);
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'BRANCH_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ShiftAssignmentResponse>> updateAssignment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody ShiftAssignmentRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        ShiftAssignmentResponse response = assignmentService.updateAssignment(tenantId, id, principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(response, "Shift assignment updated"));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'BRANCH_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ShiftAssignmentResponse>> cancelAssignment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        UUID tenantId = TenantContext.getTenantId();
        String reason = body != null ? body.get("reason") : "Manual cancellation";
        ShiftAssignmentResponse response = assignmentService.cancelAssignment(tenantId, id, principal.getUserId(), reason);
        return ResponseEntity.ok(ApiResponse.success(response, "Shift assignment cancelled"));
    }
}

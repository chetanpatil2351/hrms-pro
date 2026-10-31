package com.hrmspro.modules.shift;

import com.hrmspro.common.context.TenantContext;
import com.hrmspro.common.response.ApiResponse;
import com.hrmspro.config.security.UserPrincipal;
import com.hrmspro.modules.shift.dto.ShiftChangeRequestCreateDto;
import com.hrmspro.modules.shift.dto.ShiftChangeRequestDto;
import com.hrmspro.modules.shift.dto.ShiftChangeReviewRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shifts/change-requests")
@RequiredArgsConstructor
public class ShiftChangeRequestController {

    private final ShiftChangeRequestService changeRequestService;

    @PostMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'COMPANY_ADMIN', 'HR_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ShiftChangeRequestDto>> submitRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody ShiftChangeRequestCreateDto request) {
        UUID tenantId = TenantContext.getTenantId();
        ShiftChangeRequestDto response = changeRequestService.submitRequest(tenantId, principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Shift change request submitted"));
    }

    @GetMapping("/my-requests")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'COMPANY_ADMIN', 'HR_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<ShiftChangeRequestDto>>> getMyRequests(
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID tenantId = TenantContext.getTenantId();
        List<ShiftChangeRequestDto> list = changeRequestService.getMyRequests(tenantId, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'BRANCH_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<ShiftChangeRequestDto>>> getPendingRequests(
            @AuthenticationPrincipal UserPrincipal principal,
            Pageable pageable) {
        UUID tenantId = TenantContext.getTenantId();
        Page<ShiftChangeRequestDto> page = changeRequestService.getPendingRequests(tenantId, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'BRANCH_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ShiftChangeRequestDto>> approveRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody(required = false) ShiftChangeReviewRequest review) {
        UUID tenantId = TenantContext.getTenantId();
        ShiftChangeReviewRequest rev = review != null ? review : new ShiftChangeReviewRequest();
        ShiftChangeRequestDto response = changeRequestService.approveRequest(tenantId, principal.getUserId(), id, rev);
        return ResponseEntity.ok(ApiResponse.success(response, "Shift change request approved"));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'BRANCH_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ShiftChangeRequestDto>> rejectRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody(required = false) ShiftChangeReviewRequest review) {
        UUID tenantId = TenantContext.getTenantId();
        ShiftChangeReviewRequest rev = review != null ? review : new ShiftChangeReviewRequest();
        ShiftChangeRequestDto response = changeRequestService.rejectRequest(tenantId, principal.getUserId(), id, rev);
        return ResponseEntity.ok(ApiResponse.success(response, "Shift change request rejected"));
    }
}

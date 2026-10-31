package com.hrmspro.modules.shift;

import com.hrmspro.common.context.TenantContext;
import com.hrmspro.common.response.ApiResponse;
import com.hrmspro.config.security.UserPrincipal;
import com.hrmspro.modules.shift.dto.ShiftTemplateRequest;
import com.hrmspro.modules.shift.dto.ShiftTemplateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shifts")
@RequiredArgsConstructor
public class ShiftController {

    private final ShiftService shiftService;

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ShiftTemplateResponse>> createShift(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody ShiftTemplateRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        ShiftTemplateResponse response = shiftService.createShift(tenantId, principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Shift template created successfully"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'BRANCH_MANAGER', 'ATTENDANCE_MANAGER', 'PAYROLL_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<ShiftTemplateResponse>>> getShifts(
            @AuthenticationPrincipal UserPrincipal principal,
            Pageable pageable) {
        UUID tenantId = TenantContext.getTenantId();
        Page<ShiftTemplateResponse> page = shiftService.getShifts(tenantId, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'BRANCH_MANAGER', 'ATTENDANCE_MANAGER', 'PAYROLL_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ShiftTemplateResponse>> getShiftById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        ShiftTemplateResponse response = shiftService.getShiftById(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ShiftTemplateResponse>> updateShift(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody ShiftTemplateRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        ShiftTemplateResponse response = shiftService.updateShift(tenantId, id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Shift template updated successfully"));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ShiftTemplateResponse>> patchStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody Map<String, Boolean> body) {
        UUID tenantId = TenantContext.getTenantId();
        boolean active = body.getOrDefault("active", true);
        ShiftTemplateResponse response = shiftService.patchStatus(tenantId, id, active);
        return ResponseEntity.ok(ApiResponse.success(response, "Shift status updated successfully"));
    }
}

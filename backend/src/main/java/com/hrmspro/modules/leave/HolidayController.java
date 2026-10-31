package com.hrmspro.modules.leave;

import com.hrmspro.common.context.TenantContext;
import com.hrmspro.common.response.ApiResponse;
import com.hrmspro.config.security.UserPrincipal;
import com.hrmspro.modules.leave.dto.HolidayDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/holidays")
@RequiredArgsConstructor
public class HolidayController {

    private final HolidayService holidayService;

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<HolidayDto>> createHoliday(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody HolidayDto request) {
        UUID tenantId = TenantContext.getTenantId();
        HolidayDto response = holidayService.createHoliday(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Holiday created successfully"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'BRANCH_MANAGER', 'EMPLOYEE', 'ATTENDANCE_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<HolidayDto>>> getHolidays(
            @AuthenticationPrincipal UserPrincipal principal,
            Pageable pageable) {
        UUID tenantId = TenantContext.getTenantId();
        Page<HolidayDto> page = holidayService.getHolidays(tenantId, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'BRANCH_MANAGER', 'EMPLOYEE', 'ATTENDANCE_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<HolidayDto>> getHolidayById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        HolidayDto response = holidayService.getHolidayById(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<HolidayDto>> updateHoliday(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody HolidayDto request) {
        UUID tenantId = TenantContext.getTenantId();
        HolidayDto response = holidayService.updateHoliday(tenantId, id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Holiday updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteHoliday(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        holidayService.deleteHoliday(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(null, "Holiday deactivated successfully"));
    }
}

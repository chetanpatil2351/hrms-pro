package com.hrmspro.modules.shift;

import com.hrmspro.common.context.TenantContext;
import com.hrmspro.common.response.ApiResponse;
import com.hrmspro.config.security.UserPrincipal;
import com.hrmspro.modules.shift.dto.RosterGenerationRequest;
import com.hrmspro.modules.shift.dto.RosterResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rosters")
@RequiredArgsConstructor
public class ShiftRosterController {

    private final ShiftRosterService rosterService;

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateRoster(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody RosterGenerationRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        Map<String, Object> result = rosterService.generateRoster(tenantId, principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result, "Roster generated in DRAFT status"));
    }

    @PostMapping("/publish")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> publishRosters(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        UUID tenantId = TenantContext.getTenantId();
        Map<String, Object> result = rosterService.publishRosters(tenantId, principal.getUserId(), fromDate, toDate);
        return ResponseEntity.ok(ApiResponse.success(result, "Roster published successfully"));
    }

    @PostMapping("/cancel")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancelRosters(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        UUID tenantId = TenantContext.getTenantId();
        Map<String, Object> result = rosterService.cancelRosters(tenantId, principal.getUserId(), fromDate, toDate);
        return ResponseEntity.ok(ApiResponse.success(result, "Rosters cancelled"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'BRANCH_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<RosterResponse>>> getRosters(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID tenantId = TenantContext.getTenantId();
        List<RosterResponse> list = rosterService.getRosters(tenantId, from, to);
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'BRANCH_MANAGER', 'EMPLOYEE', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<RosterResponse>>> getRostersForEmployee(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID tenantId = TenantContext.getTenantId();
        List<RosterResponse> list = rosterService.getRostersForEmployee(tenantId, employeeId, from, to);
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/branch/{branchId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'BRANCH_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<RosterResponse>>> getRostersForBranch(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID tenantId = TenantContext.getTenantId();
        List<RosterResponse> list = rosterService.getRostersForBranch(tenantId, branchId, from, to);
        return ResponseEntity.ok(ApiResponse.success(list));
    }
}

package com.hrmspro.modules.tenant;

import com.hrmspro.common.response.ApiResponse;
import com.hrmspro.config.security.UserPrincipal;
import com.hrmspro.modules.tenant.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Tenant Management", description = "Endpoints for company onboarding, branches, departments, and invitations")
public class TenantController {

    private final TenantService tenantService;

    // --- Company Onboarding & Management ---

    @PostMapping("/tenants")
    @Operation(summary = "Create and onboard a new company (creator becomes COMPANY_ADMIN)")
    public ResponseEntity<ApiResponse<TenantResponseDto>> createTenant(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateTenantRequest request
    ) {
        TenantResponseDto response = tenantService.createTenant(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Company successfully onboarded"));
    }

    @GetMapping("/tenants/current")
    @Operation(summary = "Get active company details")
    public ResponseEntity<ApiResponse<TenantResponseDto>> getCurrentTenant(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        TenantResponseDto response = tenantService.getCurrentTenant(principal.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(response, "Current company retrieved"));
    }

    @PutMapping("/tenants/current")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Operation(summary = "Update active company details (Company Admin only)")
    public ResponseEntity<ApiResponse<TenantResponseDto>> updateCurrentTenant(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateTenantRequest request
    ) {
        TenantResponseDto response = tenantService.updateCurrentTenant(principal.getTenantId(), request);
        return ResponseEntity.ok(ApiResponse.success(response, "Company details updated"));
    }

    @GetMapping("/tenants/my-companies")
    @Operation(summary = "List all companies the user is a member of")
    public ResponseEntity<ApiResponse<List<TenantResponseDto>>> getMyCompanies(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<TenantResponseDto> response = tenantService.getMyCompanies(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response, "User companies retrieved"));
    }

    // --- Branch Management ---

    @PostMapping("/branches")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER')")
    @Operation(summary = "Create a new branch in current company")
    public ResponseEntity<ApiResponse<BranchDto>> createBranch(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody BranchDto request
    ) {
        BranchDto response = tenantService.createBranch(principal.getTenantId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Branch created successfully"));
    }

    @GetMapping("/branches")
    @Operation(summary = "List all branches of current company")
    public ResponseEntity<ApiResponse<List<BranchDto>>> getBranches(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<BranchDto> response = tenantService.getBranches(principal.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(response, "Branches retrieved"));
    }

    @GetMapping("/branches/{id}")
    @Operation(summary = "Get branch by ID")
    public ResponseEntity<ApiResponse<BranchDto>> getBranchById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        BranchDto response = tenantService.getBranchById(principal.getTenantId(), id);
        return ResponseEntity.ok(ApiResponse.success(response, "Branch retrieved"));
    }

    @PutMapping("/branches/{id}")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER')")
    @Operation(summary = "Update branch")
    public ResponseEntity<ApiResponse<BranchDto>> updateBranch(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody BranchDto request
    ) {
        BranchDto response = tenantService.updateBranch(principal.getTenantId(), id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Branch updated"));
    }

    @PatchMapping("/branches/{id}/status")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER')")
    @Operation(summary = "Toggle branch active status")
    public ResponseEntity<ApiResponse<BranchDto>> updateBranchStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam boolean active
    ) {
        BranchDto response = tenantService.updateBranchStatus(principal.getTenantId(), id, active);
        return ResponseEntity.ok(ApiResponse.success(response, "Branch status updated"));
    }

    // --- Department Management ---

    @PostMapping("/departments")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER')")
    @Operation(summary = "Create a new department in current company")
    public ResponseEntity<ApiResponse<DepartmentDto>> createDepartment(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody DepartmentDto request
    ) {
        DepartmentDto response = tenantService.createDepartment(principal.getTenantId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Department created successfully"));
    }

    @GetMapping("/departments")
    @Operation(summary = "List all departments of current company")
    public ResponseEntity<ApiResponse<List<DepartmentDto>>> getDepartments(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<DepartmentDto> response = tenantService.getDepartments(principal.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(response, "Departments retrieved"));
    }

    @GetMapping("/departments/{id}")
    @Operation(summary = "Get department by ID")
    public ResponseEntity<ApiResponse<DepartmentDto>> getDepartmentById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        DepartmentDto response = tenantService.getDepartmentById(principal.getTenantId(), id);
        return ResponseEntity.ok(ApiResponse.success(response, "Department retrieved"));
    }

    @PutMapping("/departments/{id}")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER')")
    @Operation(summary = "Update department")
    public ResponseEntity<ApiResponse<DepartmentDto>> updateDepartment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody DepartmentDto request
    ) {
        DepartmentDto response = tenantService.updateDepartment(principal.getTenantId(), id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Department updated"));
    }

    @PatchMapping("/departments/{id}/status")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER')")
    @Operation(summary = "Toggle department active status")
    public ResponseEntity<ApiResponse<DepartmentDto>> updateDepartmentStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam boolean active
    ) {
        DepartmentDto response = tenantService.updateDepartmentStatus(principal.getTenantId(), id, active);
        return ResponseEntity.ok(ApiResponse.success(response, "Department status updated"));
    }

    // --- Membership & Invitation Management ---

    @PostMapping("/members/invite")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER')")
    @Operation(summary = "Invite an employee/member to the current company")
    public ResponseEntity<ApiResponse<InvitationResponseDto>> inviteMember(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody InviteMemberRequest request
    ) {
        InvitationResponseDto response = tenantService.inviteMember(principal.getTenantId(), principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Invitation created successfully"));
    }

    @PostMapping("/members/accept-invitation")
    @Operation(summary = "Accept an invitation and join company")
    public ResponseEntity<ApiResponse<Void>> acceptInvitation(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AcceptInvitationRequest request
    ) {
        tenantService.acceptInvitation(principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(null, "Invitation accepted successfully"));
    }

    @PatchMapping("/members/{membershipId}/status")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Operation(summary = "Suspend or reactivate a member (Company Admin only)")
    public ResponseEntity<ApiResponse<Void>> updateMembershipStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID membershipId,
            @RequestParam String status
    ) {
        tenantService.updateMembershipStatus(principal.getTenantId(), membershipId, status);
        return ResponseEntity.ok(ApiResponse.success(null, "Membership status updated"));
    }
}
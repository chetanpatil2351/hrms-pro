package com.hrmspro.modules.employee;

import com.hrmspro.common.response.ApiResponse;
import com.hrmspro.config.security.UserPrincipal;
import com.hrmspro.modules.employee.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
@Tag(name = "Employee Management", description = "Endpoints for employee profiles, documents, and onboarding")
public class EmployeeController {

    private final EmployeeService employeeService;

    @PostMapping
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER')")
    @Operation(summary = "Onboard and create new employee record")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> createEmployee(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateEmployeeRequest request
    ) {
        EmployeeResponseDto response = employeeService.createEmployee(principal.getTenantId(), principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Employee record created successfully"));
    }

    @GetMapping
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER') or hasRole('BRANCH_MANAGER') or hasRole('PAYROLL_MANAGER') or hasRole('ACCOUNTANT')")
    @Operation(summary = "List and search employees with pagination and filters")
    public ResponseEntity<ApiResponse<Page<EmployeeResponseDto>>> getEmployees(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String employmentStatus,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<EmployeeResponseDto> response = employeeService.getEmployees(
                principal.getTenantId(),
                branchId,
                departmentId,
                employmentStatus,
                search,
                pageable
        );
        return ResponseEntity.ok(ApiResponse.success(response, "Employees retrieved successfully"));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user's employee profile")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> getMyProfile(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        EmployeeResponseDto response = employeeService.getCurrentEmployeeProfile(principal.getTenantId(), principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response, "Self employee profile retrieved"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER') or hasRole('BRANCH_MANAGER') or hasRole('PAYROLL_MANAGER') or hasRole('ACCOUNTANT')")
    @Operation(summary = "Get employee profile by ID")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> getEmployeeById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        EmployeeResponseDto response = employeeService.getEmployeeById(principal.getTenantId(), id);
        return ResponseEntity.ok(ApiResponse.success(response, "Employee profile retrieved"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER')")
    @Operation(summary = "Update employee profile")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> updateEmployee(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEmployeeRequest request
    ) {
        EmployeeResponseDto response = employeeService.updateEmployee(principal.getTenantId(), principal.getUserId(), id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Employee profile updated successfully"));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER')")
    @Operation(summary = "Update employee employment status")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> updateEmployeeStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam String status
    ) {
        EmployeeResponseDto response = employeeService.updateEmployeeStatus(principal.getTenantId(), principal.getUserId(), id, status);
        return ResponseEntity.ok(ApiResponse.success(response, "Employee status updated"));
    }

    @PostMapping("/{id}/documents")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER')")
    @Operation(summary = "Add document metadata for employee")
    public ResponseEntity<ApiResponse<EmployeeDocumentDto>> addDocument(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody EmployeeDocumentDto request
    ) {
        EmployeeDocumentDto response = employeeService.addEmployeeDocument(principal.getTenantId(), principal.getUserId(), id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Document recorded successfully"));
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER')")
    @Operation(summary = "List employee documents")
    public ResponseEntity<ApiResponse<List<EmployeeDocumentDto>>> getEmployeeDocuments(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        List<EmployeeDocumentDto> response = employeeService.getEmployeeDocuments(principal.getTenantId(), id);
        return ResponseEntity.ok(ApiResponse.success(response, "Employee documents retrieved"));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER')")
    @Operation(summary = "Get employee audit change history")
    public ResponseEntity<ApiResponse<List<EmployeeAuditLogDto>>> getEmployeeHistory(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        List<EmployeeAuditLogDto> response = employeeService.getEmployeeHistory(principal.getTenantId(), id);
        return ResponseEntity.ok(ApiResponse.success(response, "Employee audit history retrieved"));
    }
}
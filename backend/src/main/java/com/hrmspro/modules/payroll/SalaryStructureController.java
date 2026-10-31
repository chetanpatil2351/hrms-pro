package com.hrmspro.modules.payroll;

import com.hrmspro.common.response.ApiResponse;
import com.hrmspro.config.security.UserPrincipal;
import com.hrmspro.modules.payroll.dto.*;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/payroll")
@RequiredArgsConstructor
public class SalaryStructureController {

    private final SalaryStructureService salaryStructureService;

    // --- Salary Components ---

    @PostMapping("/components")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('HR_MANAGER')")
    public ResponseEntity<ApiResponse<SalaryComponentDto>> createComponent(
            @Valid @RequestBody SalaryComponentDto dto) {
        SalaryComponentDto created = salaryStructureService.createComponent(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Salary component created successfully"));
    }

    @GetMapping("/components")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<SalaryComponentDto>>> getActiveComponents() {
        return ResponseEntity.ok(ApiResponse.success(salaryStructureService.getActiveComponents()));
    }

    // --- Salary Structures ---

    @PostMapping("/structures")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('HR_MANAGER')")
    public ResponseEntity<ApiResponse<SalaryStructureDto>> createStructure(
            @Valid @RequestBody SalaryStructureDto dto) {
        SalaryStructureDto created = salaryStructureService.createStructure(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Salary structure created successfully"));
    }

    @GetMapping("/structures/{id}")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('HR_MANAGER')")
    public ResponseEntity<ApiResponse<SalaryStructureDto>> getStructureById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(salaryStructureService.getStructureById(id)));
    }

    @GetMapping("/structures")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('HR_MANAGER')")
    public ResponseEntity<ApiResponse<Page<SalaryStructureDto>>> getStructures(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(salaryStructureService.getStructures(pageable)));
    }

    // --- Employee Salary Assignment & Revisions ---

    @PostMapping("/assignments")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('HR_MANAGER')")
    public ResponseEntity<ApiResponse<EmployeeSalaryStructureDto>> assignStructure(
            @Valid @RequestBody EmployeeSalaryStructureDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        EmployeeSalaryStructureDto assigned = salaryStructureService.assignSalaryStructure(dto, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(assigned, "Salary structure assigned successfully"));
    }

    @GetMapping("/assignments/employee/{employeeId}")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<EmployeeSalaryStructureDto>>> getEmployeeAssignments(
            @PathVariable UUID employeeId) {
        return ResponseEntity.ok(ApiResponse.success(salaryStructureService.getEmployeeSalaryHistory(employeeId)));
    }

    @GetMapping("/revisions/employee/{employeeId}")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<SalaryRevisionDto>>> getEmployeeRevisions(
            @PathVariable UUID employeeId) {
        return ResponseEntity.ok(ApiResponse.success(salaryStructureService.getEmployeeRevisionHistory(employeeId)));
    }
}

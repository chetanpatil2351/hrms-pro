package com.hrmspro.modules.payroll;

import com.hrmspro.common.response.ApiResponse;
import com.hrmspro.config.security.UserPrincipal;
import com.hrmspro.common.exception.ResourceNotFoundException;
import com.hrmspro.modules.employee.Employee;
import com.hrmspro.modules.employee.EmployeeRepository;
import com.hrmspro.modules.payroll.dto.PayslipDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payroll/payslips")
@RequiredArgsConstructor
public class PayslipController {

    private final PayrollService payrollService;
    private final EmployeeRepository employeeRepository;

    @GetMapping("/my-payslips")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<PayslipDto>>> getMyPayslips(
            @AuthenticationPrincipal UserPrincipal principal) {
        Employee employee = employeeRepository.findByTenantIdAndUserId(principal.getTenantId(), principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("No employee profile linked to current user"));

        return ResponseEntity.ok(ApiResponse.success(payrollService.getEmployeePayslips(employee.getId())));
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<PayslipDto>>> getEmployeePayslips(
            @PathVariable UUID employeeId) {
        return ResponseEntity.ok(ApiResponse.success(payrollService.getEmployeePayslips(employeeId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PayslipDto>> getPayslipById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        PayslipDto payslip = payrollService.getPayslipById(id);
        boolean isHrOrAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ADMIN") || a.getAuthority().equals("HR_MANAGER"));
        if (!isHrOrAdmin) {
            Employee employee = employeeRepository.findByTenantIdAndUserId(principal.getTenantId(), principal.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Employee profile not found"));
            if (!payslip.getEmployeeId().equals(employee.getId())) {
                throw new org.springframework.security.access.AccessDeniedException("Cannot access another employee's payslip");
            }
        }
        return ResponseEntity.ok(ApiResponse.success(payslip));
    }
}

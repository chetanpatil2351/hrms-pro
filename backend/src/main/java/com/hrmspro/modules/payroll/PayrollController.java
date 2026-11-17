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
public class PayrollController {

    private final PayrollService payrollService;

    // --- Preview ---
    @PostMapping("/periods/{id}/preview")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('HR_MANAGER')")
    public ResponseEntity<ApiResponse<PayrollPreviewDto>> previewPayroll(@PathVariable UUID id) {
        PayrollPreviewDto preview = payrollService.previewPayrollRun(id);
        return ResponseEntity.ok(ApiResponse.success(preview, "Payroll preview generated successfully"));
    }

    // --- History: list runs for a period ---
    @GetMapping("/periods/{id}/runs")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<PayrollRunDto>>> getPeriodRuns(@PathVariable UUID id) {
        List<PayrollRunDto> runs = payrollService.listRuns(id);
        return ResponseEntity.ok(ApiResponse.success(runs));
    }

    // --- History: run details ---
    @GetMapping("/runs/{runId}")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('HR_MANAGER')")
    public ResponseEntity<ApiResponse<PayrollRunResultDto>> getRunDetails(@PathVariable UUID runId) {
        PayrollRunResultDto details = payrollService.getRunDetails(runId);
        return ResponseEntity.ok(ApiResponse.success(details));
    }

    // Existing endpoints (statutory rules, periods, calculate, approve, finalize, adjustments) follow below...

    @PostMapping("/statutory-rules")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('HR_MANAGER')")
    public ResponseEntity<ApiResponse<StatutoryRuleDto>> configureStatutoryRule(
            @Valid @RequestBody StatutoryRuleDto dto) {
        StatutoryRuleDto created = payrollService.configureStatutoryRule(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Statutory rule configured successfully"));
    }

    @GetMapping("/statutory-rules")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<StatutoryRuleDto>>> getStatutoryRules(
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.success(payrollService.getStatutoryRules(year)));
    }

    // --- Periods ---

    @PostMapping("/periods")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('HR_MANAGER')")
    public ResponseEntity<ApiResponse<PayrollPeriodDto>> createPeriod(
            @Valid @RequestBody PayrollPeriodDto dto) {
        PayrollPeriodDto created = payrollService.createPeriod(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Payroll period created successfully"));
    }

    @GetMapping("/periods")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('HR_MANAGER')")
    public ResponseEntity<ApiResponse<Page<PayrollPeriodDto>>> getPeriods(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(payrollService.getPeriods(pageable)));
    }

    @GetMapping("/periods/{id}")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('HR_MANAGER')")
    public ResponseEntity<ApiResponse<PayrollPeriodDto>> getPeriod(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(payrollService.getPeriod(id)));
    }

    // --- Calculation, Approval, Finalization ---

    @PostMapping("/periods/{id}/calculate")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('HR_MANAGER')")
    public ResponseEntity<ApiResponse<PayrollRunResultDto>> calculatePayroll(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        PayrollRunResultDto result = payrollService.calculatePayrollRun(id, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(result, "Payroll calculated successfully"));
    }

    @PostMapping("/periods/{id}/approve")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('HR_MANAGER')")
    public ResponseEntity<ApiResponse<PayrollPeriodDto>> approvePayroll(
            @PathVariable UUID id) {
        PayrollPeriodDto result = payrollService.approvePeriod(id);
        return ResponseEntity.ok(ApiResponse.success(result, "Payroll approved successfully"));
    }

    @PostMapping("/periods/{id}/finalize")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<PayrollPeriodDto>> finalizePayroll(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        PayrollPeriodDto result = payrollService.finalizePeriod(id, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(result, "Payroll finalized and locked successfully"));
    }

    // --- Adjustments ---

    @PostMapping("/adjustments")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('HR_MANAGER')")
    public ResponseEntity<ApiResponse<PayrollAdjustmentDto>> addAdjustment(
            @Valid @RequestBody PayrollAdjustmentDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        PayrollAdjustmentDto result = payrollService.addAdjustment(dto, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result, "Payroll adjustment recorded successfully"));
    }
}

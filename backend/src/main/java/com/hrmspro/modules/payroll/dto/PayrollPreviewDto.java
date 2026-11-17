package com.hrmspro.modules.payroll.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTO returned by the non‑persisting payroll preview endpoint.
 * It mirrors the structure of {@link PayrollRunResultDto} but does not contain a runId
 * and its status is always "PREVIEW". No persistence identifiers are exposed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollPreviewDto {
    private UUID periodId;
    @Builder.Default
    private String status = "PREVIEW";
    private Integer processedEmployees;
    private BigDecimal totalGross;
    private BigDecimal totalDeductions;
    private BigDecimal totalNet;
    @Builder.Default
    private List<EmployeePayrollSummaryDto> employeeSummaries = new ArrayList<>();

    public List<EmployeePayrollSummaryDto> getEmployeePayrollSummaries() {
        return employeeSummaries;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmployeePayrollSummaryDto {
        private UUID employeeId;
        private String employeeCode;
        private String employeeName;
        private Integer totalDays;
        private BigDecimal payableDays;
        private BigDecimal unpaidLeaveDays;
        private BigDecimal grossEarnings;
        private BigDecimal totalDeductions;
        private BigDecimal employerContributions;
        private BigDecimal netSalary;
        @Builder.Default
        private List<LineItemDto> lineItems = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItemDto {
        private String componentCode;
        private String componentName;
        private String type; // EARNING, DEDUCTION, EMPLOYER_CONTRIBUTION
        private BigDecimal amount;
    }
}

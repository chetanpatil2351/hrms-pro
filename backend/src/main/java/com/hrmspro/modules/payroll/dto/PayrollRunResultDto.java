package com.hrmspro.modules.payroll.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollRunResultDto {
    private UUID runId;
    private UUID periodId;
    private Integer runNumber;
    private String runType;
    private String status;
    private Integer processedEmployees;
    private BigDecimal totalGross;
    private BigDecimal totalDeductions;
    private BigDecimal totalNet;
    private List<EmployeePayrollSummaryDto> employeeSummaries;

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
        private List<LineItemDto> lineItems;
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

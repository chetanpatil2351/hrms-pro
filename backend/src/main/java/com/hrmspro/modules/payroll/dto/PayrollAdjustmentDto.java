package com.hrmspro.modules.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollAdjustmentDto {
    private UUID id;
    @NotNull(message = "Employee ID is required")
    private UUID employeeId;
    private String employeeName;
    private UUID payrollPeriodId;
    @NotBlank(message = "Adjustment type is required (ADVANCE_DEDUCTION, LOAN_EMI, REIMBURSEMENT, BONUS, ARREARS)")
    private String adjustmentType;
    @NotNull(message = "Amount is required")
    private BigDecimal amount;
    @NotBlank(message = "Reason is required")
    private String reason;
    private Boolean isProcessed;
    private String idempotencyKey;
    private UUID authorizedBy;
}

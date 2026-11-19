package com.hrmspro.modules.payroll.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for creating a salary advance request.
 * Only client‑editable fields are exposed.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalaryAdvanceRequestDto {

    @NotNull
    @Positive
    private BigDecimal requestedAmount;

    // Optional free‑form notes or reason for the advance.
    private String notes;

    // Number of equal installments the employee prefers.
    @Positive
    private Integer installmentCount;
}

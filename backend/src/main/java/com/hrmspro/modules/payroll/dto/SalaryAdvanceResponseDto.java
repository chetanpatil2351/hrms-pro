package com.hrmspro.modules.payroll.dto;

import com.hrmspro.modules.payroll.entity.SalaryAdvanceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO for returning salary advance details.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalaryAdvanceResponseDto {

    private UUID id;

    private UUID employeeId;

    private BigDecimal requestedAmount;

    private BigDecimal approvedAmount;

    private SalaryAdvanceStatus status;

    private Instant requestDate;

    private Instant decisionDate;

    private LocalDate repaymentStartDate;

    private Integer installmentCount;

    private BigDecimal installmentAmount;

    private BigDecimal outstandingBalance;

    private String notes;

    private UUID approverId;
}

package com.hrmspro.modules.payroll.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight DTO for listing payroll runs belonging to a period.
 * It contains only metadata – no line‑item details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollRunDto {
    private UUID runId;
    private UUID periodId;
    private Integer runNumber;
    private String runType;
    private String status;
    private Instant createdAt;
    private Instant completedAt;
    private Integer processedEmployees;
    private BigDecimal totalGross;
    private BigDecimal totalDeductions;
    private BigDecimal totalNet;
}

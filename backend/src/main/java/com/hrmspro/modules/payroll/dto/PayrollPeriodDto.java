package com.hrmspro.modules.payroll.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollPeriodDto {
    private UUID id;
    @NotNull(message = "Year is required")
    private Integer year;
    @NotNull(message = "Month is required (1-12)")
    private Integer month;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Integer totalEmployees;
    private BigDecimal totalGrossPay;
    private BigDecimal totalNetPay;
    private BigDecimal totalEmployerCost;
    private UUID finalizedBy;
    private Instant finalizedAt;
}

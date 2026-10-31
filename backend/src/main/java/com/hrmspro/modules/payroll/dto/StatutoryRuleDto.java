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
public class StatutoryRuleDto {
    private UUID id;
    @NotBlank(message = "Rule type is required (EPF, ESI, PROFESSIONAL_TAX, TDS)")
    private String ruleType;
    private String state;
    @NotNull(message = "Effective year is required")
    private Integer effectiveYear;
    private BigDecimal employeeRate;
    private BigDecimal employerRate;
    private BigDecimal wageCeiling;
    private BigDecimal minimumWageThreshold;
    @Builder.Default
    private Boolean isActive = true;
}

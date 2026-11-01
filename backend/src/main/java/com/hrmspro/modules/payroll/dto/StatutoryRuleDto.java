package com.hrmspro.modules.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    // New effective‑dated fields
    @NotNull(message = "Effective from date required")
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    @NotBlank(message = "Jurisdiction required")
    private String jurisdiction;
    private String sourceReference;
    private String ruleVersion;
    private BigDecimal disabilityCeiling;
}

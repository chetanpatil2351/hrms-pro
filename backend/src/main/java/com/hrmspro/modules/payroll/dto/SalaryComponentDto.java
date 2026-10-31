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
public class SalaryComponentDto {
    private UUID id;
    @NotBlank(message = "Component name is required")
    private String name;
    @NotBlank(message = "Component code is required")
    private String code;
    @NotBlank(message = "Component type is required")
    private String type; // EARNING, DEDUCTION, EMPLOYER_CONTRIBUTION
    @Builder.Default
    private String calculationType = "FIXED_AMOUNT"; // FIXED_AMOUNT, PERCENTAGE
    private String calculationBase;
    private BigDecimal percentageRate;
    @Builder.Default
    private Boolean isTaxable = true;
    @Builder.Default
    private Boolean includeInGross = true;
    @Builder.Default
    private Boolean includeInCTC = true;
    @Builder.Default
    private Boolean isStatutory = false;
    @Builder.Default
    private Boolean isActive = true;
}

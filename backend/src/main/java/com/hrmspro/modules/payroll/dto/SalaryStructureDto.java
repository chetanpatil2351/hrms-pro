package com.hrmspro.modules.payroll.dto;

import jakarta.validation.constraints.NotBlank;
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
public class SalaryStructureDto {
    private UUID id;
    @NotBlank(message = "Structure name is required")
    private String name;
    @NotBlank(message = "Structure code is required")
    private String code;
    private String description;
    @Builder.Default
    private Boolean isActive = true;
    private List<ComponentItemDto> components;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentItemDto {
        private UUID componentId;
        private String componentCode;
        private String componentName;
        private String componentType;
        private BigDecimal defaultAmount;
        private BigDecimal percentageRate;
        private Integer displayOrder;
    }
}

package com.hrmspro.modules.payroll.dto;

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
public class EmployeeSalaryStructureDto {
    private UUID id;
    @NotNull(message = "Employee ID is required")
    private UUID employeeId;
    private String employeeCode;
    private String employeeName;
    @NotNull(message = "Salary structure ID is required")
    private UUID salaryStructureId;
    private String salaryStructureName;
    private Integer version;
    @NotNull(message = "Effective from date is required")
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    @NotNull(message = "Annual CTC is required")
    private BigDecimal annualCtc;
    private BigDecimal monthlyGross;
    private BigDecimal monthlyBaseSalary;
    @Builder.Default
    private Boolean isActive = true;
    private String revisionReason;
}

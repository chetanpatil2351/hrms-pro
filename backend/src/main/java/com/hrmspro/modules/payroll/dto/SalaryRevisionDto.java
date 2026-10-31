package com.hrmspro.modules.payroll.dto;

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
public class SalaryRevisionDto {
    private UUID id;
    private UUID employeeId;
    private String employeeName;
    private UUID previousStructureId;
    private String previousStructureName;
    private UUID newStructureId;
    private String newStructureName;
    private BigDecimal previousAnnualCtc;
    private BigDecimal newAnnualCtc;
    private LocalDate revisionDate;
    private String revisionReason;
    private UUID revisedBy;
}

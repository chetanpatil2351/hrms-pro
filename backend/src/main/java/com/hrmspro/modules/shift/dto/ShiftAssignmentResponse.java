package com.hrmspro.modules.shift.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class ShiftAssignmentResponse {

    private UUID id;
    private UUID tenantId;
    private UUID employeeId;
    private String employeeName;
    private String employeeCode;

    private UUID shiftTemplateId;
    private String shiftName;
    private String shiftCode;

    private String assignmentType;
    private LocalDate effectiveStart;
    private LocalDate effectiveEnd;
    private String status;

    private Integer rotationCycleDays;
    private Integer rotationSequence;

    private String notes;
}

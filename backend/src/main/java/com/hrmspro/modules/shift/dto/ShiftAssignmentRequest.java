package com.hrmspro.modules.shift.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class ShiftAssignmentRequest {

    private UUID employeeId;
    private UUID shiftTemplateId;

    private String assignmentType; // PERMANENT, TEMPORARY, ROTATING
    private LocalDate effectiveStart;
    private LocalDate effectiveEnd; // null = open-ended (permanent)

    private Integer rotationCycleDays;
    private Integer rotationSequence;

    private String notes;
}

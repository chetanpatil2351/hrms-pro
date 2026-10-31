package com.hrmspro.modules.shift.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class BulkShiftAssignmentRequest {

    private List<UUID> employeeIds;
    private UUID shiftTemplateId;

    private String assignmentType; // PERMANENT, TEMPORARY
    private LocalDate effectiveStart;
    private LocalDate effectiveEnd; // null = open-ended

    private String notes;
    private boolean skipConflicting; // if true, skip employees with overlapping assignments instead of failing
}

package com.hrmspro.modules.shift.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class ShiftChangeRequestDto {

    private UUID id;
    private UUID employeeId;
    private String employeeName;

    private UUID currentAssignmentId;
    private String currentShiftName;

    private UUID requestedShiftId;
    private String requestedShiftName;
    private LocalDate requestedEffectiveDate;
    private LocalDate requestedEffectiveEnd;

    private String reason;
    private String status;

    private String reviewerRemarks;
    private Instant reviewedAt;

    private Instant createdAt;
}

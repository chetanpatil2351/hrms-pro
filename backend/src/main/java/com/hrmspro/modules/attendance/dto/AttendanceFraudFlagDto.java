package com.hrmspro.modules.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceFraudFlagDto {
    private UUID id;
    private UUID employeeId;
    private String employeeName;
    private String employeeCode;
    private UUID attendanceRecordId;
    private UUID attendanceEventId;
    private String flagType;
    private String severity;
    private String reason;
    private String details;
    private String status;
    private UUID reviewedById;
    private String reviewedByName;
    private Instant reviewedAt;
    private String reviewRemarks;
    private Instant createdAt;
}

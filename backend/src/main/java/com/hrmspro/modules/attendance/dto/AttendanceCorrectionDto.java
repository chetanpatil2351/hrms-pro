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
public class AttendanceCorrectionDto {
    private UUID id;
    private UUID attendanceRecordId;
    private UUID employeeId;
    private String employeeName;
    private Instant requestedCheckIn;
    private Instant requestedCheckOut;
    private String reason;
    private String status;
    private String reviewRemarks;
    private Instant reviewedAt;
    private Instant createdAt;
}
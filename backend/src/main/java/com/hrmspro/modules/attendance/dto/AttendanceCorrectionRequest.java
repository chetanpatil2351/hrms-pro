package com.hrmspro.modules.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class AttendanceCorrectionRequest {
    @NotNull(message = "Attendance record ID is required")
    private UUID attendanceRecordId;

    private Instant requestedCheckIn;
    private Instant requestedCheckOut;

    @NotBlank(message = "Reason for correction is required")
    private String reason;
}
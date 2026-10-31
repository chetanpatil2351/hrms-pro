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
public class AttendanceBreakDto {
    private UUID id;
    private String breakType;
    private Instant startTime;
    private Instant endTime;
    private Integer durationMinutes;
}
package com.hrmspro.modules.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceResponseDto {
    private UUID id;
    private UUID employeeId;
    private String employeeName;
    private String employeeCode;
    private UUID branchId;
    private String branchName;
    private LocalDate attendanceDate;

    private Instant checkInTime;
    private Instant checkOutTime;
    private String status;

    private Integer workDurationMinutes;
    private Integer breakDurationMinutes;
    private Integer netWorkDurationMinutes;

    private Boolean isLate;
    private Boolean isEarlyDeparture;
    private Boolean isGeofenceVerified;
    private Boolean isSelfieVerified;

    private Integer sessionNumber;
    private UUID assignedShiftId;
    private Integer scheduledWorkMinutes;
    private Integer lateByMinutes;
    private Integer earlyDepartureByMinutes;

    private List<AttendanceBreakDto> breaks;
}
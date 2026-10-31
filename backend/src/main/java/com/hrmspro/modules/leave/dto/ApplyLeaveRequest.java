package com.hrmspro.modules.leave.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class ApplyLeaveRequest {
    private UUID leaveTypeId;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean halfDay;
    private String halfDaySession; // FIRST_HALF, SECOND_HALF
    private String reason;
    private String attachmentUrl;
}

package com.hrmspro.modules.leave.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class LeaveApplicationDto {
    private UUID id;
    private UUID employeeId;
    private String employeeName;
    private String employeeCode;
    private UUID leaveTypeId;
    private String leaveTypeCode;
    private String leaveTypeName;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isHalfDay;
    private String halfDaySession;
    private BigDecimal totalDays;
    private String reason;
    private String attachmentUrl;
    private String status;
    private Instant createdAt;
    private List<LeaveApprovalStepDto> approvalSteps;
}

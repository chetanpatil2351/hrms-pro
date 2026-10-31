package com.hrmspro.modules.leave.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class LeaveApprovalStepDto {
    private UUID id;
    private Integer stepNumber;
    private UUID approverUserId;
    private String approverName;
    private String status;
    private String comments;
    private Instant actionTime;
}

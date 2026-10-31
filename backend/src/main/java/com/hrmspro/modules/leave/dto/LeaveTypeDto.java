package com.hrmspro.modules.leave.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class LeaveTypeDto {
    private UUID id;
    private String name;
    private String code;
    private String description;
    private Boolean isPaid;
    private String category;
    private Boolean isHalfDayAllowed;
    private Boolean requiresAttachment;
    private Integer attachmentMandatoryDays;
    private Boolean isActive;
}

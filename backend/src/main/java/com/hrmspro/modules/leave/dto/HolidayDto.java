package com.hrmspro.modules.leave.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class HolidayDto {
    private UUID id;
    private UUID branchId;
    private String branchName;
    private String name;
    private LocalDate holidayDate;
    private String description;
    private String holidayType;
    private Boolean isRecurring;
    private Boolean isActive;
}

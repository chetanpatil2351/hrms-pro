package com.hrmspro.modules.shift.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ShiftTemplateResponse {

    private UUID id;
    private UUID tenantId;
    private UUID branchId;
    private String branchName;

    private String name;
    private String code;
    private String description;

    private LocalTime startTime;
    private LocalTime endTime;
    private Boolean isOvernight;
    private String timezone;
    private Integer totalScheduledMinutes;

    private Integer gracePeriodMinutes;
    private String lateArrivalPolicy;
    private String earlyDeparturePolicy;

    private String breakPolicy;
    private Integer breakAllowedMinutes;

    private List<String> weeklyOffDays;

    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Boolean isActive;
}

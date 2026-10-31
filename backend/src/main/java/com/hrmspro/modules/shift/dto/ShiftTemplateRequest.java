package com.hrmspro.modules.shift.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Data
public class ShiftTemplateRequest {

    private UUID branchId; // null = all branches

    private String name;
    private String code;
    private String description;

    private LocalTime startTime;
    private LocalTime endTime;
    private Boolean isOvernight;
    private String timezone; // IANA zone ID e.g. "Asia/Kolkata"

    private Integer gracePeriodMinutes;
    private String lateArrivalPolicy;    // MARK_LATE, HALF_DAY, ABSENT
    private String earlyDeparturePolicy; // MARK_EARLY, HALF_DAY

    private String breakPolicy;          // NONE, FLEXIBLE, FIXED
    private Integer breakAllowedMinutes;

    /**
     * JSONB array of uppercase day names.
     * Allowed values: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
     */
    private List<String> weeklyOffDays;

    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Boolean isActive;
}

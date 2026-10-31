package com.hrmspro.modules.shift.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class RosterResponse {

    private UUID id;
    private UUID employeeId;
    private String employeeName;
    private String employeeCode;
    private UUID shiftTemplateId;
    private String shiftName;
    private String shiftCode;

    private LocalDate rosterDate;
    private Boolean isWeeklyOff;
    private Boolean isHoliday;
    private Instant scheduledStartUtc;
    private Instant scheduledEndUtc;
    private Integer scheduledMinutes;

    private String status; // DRAFT, PUBLISHED, CANCELLED
    private Instant publishedAt;
    private String notes;
}

package com.hrmspro.modules.shift.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class ShiftChangeRequestCreateDto {
    private UUID requestedShiftId;
    private LocalDate requestedEffectiveDate;
    private LocalDate requestedEffectiveEnd;
    private String reason;
}

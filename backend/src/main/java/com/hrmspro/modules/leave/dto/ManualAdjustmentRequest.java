package com.hrmspro.modules.leave.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class ManualAdjustmentRequest {
    private UUID employeeId;
    private UUID leaveTypeId;
    private Integer cycleYear;
    private BigDecimal amount; // positive = credit, negative = debit
    private String reason;
}

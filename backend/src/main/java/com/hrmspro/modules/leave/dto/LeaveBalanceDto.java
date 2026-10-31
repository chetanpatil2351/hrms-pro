package com.hrmspro.modules.leave.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class LeaveBalanceDto {
    private UUID id;
    private UUID employeeId;
    private String employeeName;
    private String employeeCode;
    private UUID leaveTypeId;
    private String leaveTypeCode;
    private String leaveTypeName;
    private Integer cycleYear;

    private BigDecimal openingBalance;
    private BigDecimal accruedAmount;
    private BigDecimal creditedAmount;
    private BigDecimal usedAmount;
    private BigDecimal pendingApprovalAmount;
    private BigDecimal expiredAmount;
    private BigDecimal carriedForwardAmount;
    private BigDecimal availableBalance;
}

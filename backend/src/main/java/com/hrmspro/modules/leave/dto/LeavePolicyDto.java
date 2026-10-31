package com.hrmspro.modules.leave.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class LeavePolicyDto {
    private UUID id;
    private UUID leaveTypeId;
    private String leaveTypeCode;
    private String leaveTypeName;
    private String name;
    private BigDecimal allocationDays;
    private String accrualFrequency;
    private BigDecimal accrualRate;
    private Integer maxConsecutiveDays;
    private Integer minNoticeDays;
    private Boolean allowCarryForward;
    private BigDecimal maxCarryForwardDays;
    private Integer carryForwardExpiryMonths;
    private Boolean allowNegativeBalance;
    private BigDecimal maxNegativeBalance;
    private Boolean countWeeklyOffAsLeave;
    private Boolean countHolidayAsLeave;
    private Integer policyEffectiveYear;
    private Boolean isActive;
}

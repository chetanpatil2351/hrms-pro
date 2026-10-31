package com.hrmspro.modules.leave;

import com.hrmspro.common.base.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(
    name = "leave_policies",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_leave_policy_tenant_type_year", columnNames = {"tenant_id", "leave_type_id", "policy_effective_year"})
    }
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeavePolicy extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(nullable = false, length = 255)
    private String name;

    @Builder.Default
    @Column(name = "allocation_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal allocationDays = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "accrual_frequency", nullable = false, length = 50)
    private String accrualFrequency = "MONTHLY"; // NONE, MONTHLY, QUARTERLY, ANNUAL, ON_JOINING

    @Builder.Default
    @Column(name = "accrual_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal accrualRate = BigDecimal.ONE;

    @Column(name = "max_consecutive_days")
    private Integer maxConsecutiveDays;

    @Builder.Default
    @Column(name = "min_notice_days", nullable = false)
    private Integer minNoticeDays = 0;

    @Builder.Default
    @Column(name = "allow_carry_forward", nullable = false)
    private Boolean allowCarryForward = false;

    @Builder.Default
    @Column(name = "max_carry_forward_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxCarryForwardDays = BigDecimal.ZERO;

    @Column(name = "carry_forward_expiry_months")
    private Integer carryForwardExpiryMonths;

    @Builder.Default
    @Column(name = "allow_negative_balance", nullable = false)
    private Boolean allowNegativeBalance = false;

    @Builder.Default
    @Column(name = "max_negative_balance", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxNegativeBalance = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "count_weekly_off_as_leave", nullable = false)
    private Boolean countWeeklyOffAsLeave = false;

    @Builder.Default
    @Column(name = "count_holiday_as_leave", nullable = false)
    private Boolean countHolidayAsLeave = false;

    @Column(name = "policy_effective_year", nullable = false)
    private Integer policyEffectiveYear;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}

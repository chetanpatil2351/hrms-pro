package com.hrmspro.modules.shift;

import com.hrmspro.common.base.BaseTenantEntity;
import com.hrmspro.modules.tenant.Branch;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Shift Template: Defines a reusable shift configuration.
 * weekly_off_days is stored as a JSONB column containing a JSON array string
 * e.g. ["SATURDAY","SUNDAY"]. Serialization/validation is done by ShiftService using Jackson.
 * Timezone is an IANA zone ID (e.g. "Asia/Kolkata", "America/New_York").
 * DST policy: for a spring-forward day (23h), scheduled_minutes reflects actual
 * clock time and attendance is credited with actual worked minutes. For a fall-back
 * day (25h), the extra hour is flagged but not auto-credited as overtime.
 */
@Getter
@Setter
@Entity
@Table(
    name = "shift_templates",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_tenant_shift_code", columnNames = {"tenant_id", "code"})
    }
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftTemplate extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch; // null = applies to all branches in tenant

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Builder.Default
    @Column(name = "is_overnight", nullable = false)
    private Boolean isOvernight = false;

    @Column(nullable = false, length = 100)
    @Builder.Default
    private String timezone = "Asia/Kolkata";

    @Column(name = "total_scheduled_minutes", nullable = false)
    private Integer totalScheduledMinutes;

    @Builder.Default
    @Column(name = "grace_period_minutes", nullable = false)
    private Integer gracePeriodMinutes = 0;

    @Builder.Default
    @Column(name = "late_arrival_policy", nullable = false, length = 50)
    private String lateArrivalPolicy = "MARK_LATE"; // MARK_LATE, HALF_DAY, ABSENT

    @Builder.Default
    @Column(name = "early_departure_policy", nullable = false, length = 50)
    private String earlyDeparturePolicy = "MARK_EARLY"; // MARK_EARLY, HALF_DAY

    @Builder.Default
    @Column(name = "break_policy", nullable = false, length = 50)
    private String breakPolicy = "FLEXIBLE"; // NONE, FLEXIBLE, FIXED

    @Builder.Default
    @Column(name = "break_allowed_minutes", nullable = false)
    private Integer breakAllowedMinutes = 0;

    /**
     * Stored as JSON string in the database (jsonb column).
     * Contains a JSON array of uppercase day names e.g. '["SATURDAY","SUNDAY"]'.
     * Use ShiftService.parseWeeklyOffDays() and serializeWeeklyOffDays() to convert.
     * Validated to contain only: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY.
     */
    @Column(name = "weekly_off_days", columnDefinition = "TEXT", nullable = false)
    @Builder.Default
    private String weeklyOffDays = "[]";

    @Column(name = "effective_from", nullable = false)
    @Builder.Default
    private LocalDate effectiveFrom = LocalDate.now();

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}

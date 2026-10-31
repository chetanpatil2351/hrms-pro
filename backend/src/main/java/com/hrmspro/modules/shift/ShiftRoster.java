package com.hrmspro.modules.shift;

import com.hrmspro.common.base.BaseTenantEntity;
import com.hrmspro.modules.employee.Employee;
import com.hrmspro.modules.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * ShiftRoster: Pre-computed daily schedule record per employee.
 * Status lifecycle: DRAFT → PUBLISHED → CANCELLED
 * Only PUBLISHED rosters affect attendance integration calculations.
 * shift_snapshot is a JSON string copy of shift template fields at generation time (immutable).
 */
@Getter
@Setter
@Entity
@Table(
    name = "shift_rosters",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_roster_employee_date", columnNames = {"tenant_id", "employee_id", "roster_date"})
    }
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftRoster extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_assignment_id")
    private EmployeeShiftAssignment shiftAssignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_template_id")
    private ShiftTemplate shiftTemplate;

    @Column(name = "roster_date", nullable = false)
    private LocalDate rosterDate;

    @Builder.Default
    @Column(name = "is_weekly_off", nullable = false)
    private Boolean isWeeklyOff = false;

    @Builder.Default
    @Column(name = "is_holiday", nullable = false)
    private Boolean isHoliday = false;

    @Column(name = "scheduled_start_utc")
    private Instant scheduledStartUtc;

    @Column(name = "scheduled_end_utc")
    private Instant scheduledEndUtc;

    @Column(name = "scheduled_minutes")
    private Integer scheduledMinutes;

    /**
     * JSON string snapshot of shift template at generation time.
     * Never updated when parent ShiftTemplate is modified.
     */
    @Column(name = "shift_snapshot", columnDefinition = "TEXT")
    private String shiftSnapshot;

    @Builder.Default
    @Column(nullable = false, length = 50)
    private String status = "DRAFT"; // DRAFT, PUBLISHED, CANCELLED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_by")
    private AppUser generatedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by")
    private AppUser publishedBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private AppUser cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(columnDefinition = "TEXT")
    private String notes;
}

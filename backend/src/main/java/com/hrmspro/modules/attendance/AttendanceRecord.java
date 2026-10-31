package com.hrmspro.modules.attendance;

import com.hrmspro.common.base.BaseTenantEntity;
import com.hrmspro.modules.employee.Employee;
import com.hrmspro.modules.tenant.Branch;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "attendance_records")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceRecord extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "check_in_time", nullable = false)
    private Instant checkInTime;

    @Column(name = "check_out_time")
    private Instant checkOutTime;

    @Builder.Default
    @Column(nullable = false, length = 50)
    private String status = "IN_PROGRESS"; // IN_PROGRESS, PRESENT, HALF_DAY, OVERTIME, INCOMPLETE, ON_LEAVE

    @Builder.Default
    @Column(name = "work_duration_minutes")
    private Integer workDurationMinutes = 0;

    @Builder.Default
    @Column(name = "break_duration_minutes")
    private Integer breakDurationMinutes = 0;

    @Builder.Default
    @Column(name = "net_work_duration_minutes")
    private Integer netWorkDurationMinutes = 0;

    @Builder.Default
    @Column(name = "is_late", nullable = false)
    private Boolean isLate = false;

    @Builder.Default
    @Column(name = "is_early_departure", nullable = false)
    private Boolean isEarlyDeparture = false;

    @Builder.Default
    @Column(name = "is_geofence_verified", nullable = false)
    private Boolean isGeofenceVerified = true;

    @Builder.Default
    @Column(name = "is_selfie_verified", nullable = false)
    private Boolean isSelfieVerified = false;

    @Builder.Default
    @OneToMany(mappedBy = "attendanceRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AttendanceEvent> events = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "attendanceRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AttendanceBreak> breaks = new ArrayList<>();

    // --- Phase 1G: Shift integration fields (additive, nullable for historical records) ---

    /**
     * Session discriminator for split-shift / multiple daily check-in sessions.
     * Default = 1 for all single-session employees.
     * Allocated atomically using a pessimistic lock query in AttendanceService.
     */
    @Builder.Default
    @Column(name = "session_number", nullable = false)
    private Integer sessionNumber = 1;

    /**
     * FK to the shift assignment active on this attendance_date.
     * Null for records created before Phase 1G (historical) or for unscheduled employees.
     */
    @Column(name = "assigned_shift_id")
    private java.util.UUID assignedShiftId;

    /**
     * JSON snapshot of shift template at check-in time.
     * Immutable — never overwritten if the parent shift template changes.
     */
    @Column(name = "shift_snapshot", columnDefinition = "TEXT")
    private String shiftSnapshot;

    /** Pre-computed scheduled start in UTC at check-in time (snapshot). */
    @Column(name = "scheduled_start_utc")
    private Instant scheduledStartUtc;

    /** Pre-computed scheduled end in UTC at check-in time (accounts for overnight). */
    @Column(name = "scheduled_end_utc")
    private Instant scheduledEndUtc;

    /** Snapshot of total scheduled working minutes from shift template. */
    @Column(name = "scheduled_work_minutes")
    private Integer scheduledWorkMinutes;

    /** Minutes late beyond grace period; 0 when on time or no shift assigned. */
    @Builder.Default
    @Column(name = "late_by_minutes")
    private Integer lateByMinutes = 0;

    /** Minutes of early departure before scheduled end; 0 when departed on time. */
    @Builder.Default
    @Column(name = "early_departure_by_minutes")
    private Integer earlyDepartureByMinutes = 0;

    /**
     * FK to the approved leave application covering this date.
     * Set when leave is approved to reflect status ON_LEAVE without deleting punches.
     */
    @Column(name = "leave_application_id")
    private java.util.UUID leaveApplicationId;
}
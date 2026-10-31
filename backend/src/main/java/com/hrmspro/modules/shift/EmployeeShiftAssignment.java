package com.hrmspro.modules.shift;

import com.hrmspro.common.base.BaseTenantEntity;
import com.hrmspro.modules.employee.Employee;
import com.hrmspro.modules.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Employee Shift Assignment: Links an employee to a ShiftTemplate for a date range.
 * Open-ended assignments have effectiveEnd = null (permanent).
 * Overlap detection is performed in the service layer using interval-based queries.
 */
@Getter
@Setter
@Entity
@Table(name = "employee_shift_assignments")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeShiftAssignment extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_template_id", nullable = false)
    private ShiftTemplate shiftTemplate;

    /**
     * PERMANENT: open-ended. TEMPORARY: bounded by effectiveEnd. ROTATING: cyclic.
     */
    @Builder.Default
    @Column(name = "assignment_type", nullable = false, length = 50)
    private String assignmentType = "PERMANENT";

    @Column(name = "effective_start", nullable = false)
    private LocalDate effectiveStart;

    @Column(name = "effective_end")
    private LocalDate effectiveEnd; // null = open-ended

    /**
     * ACTIVE: in force. CANCELLED: manually cancelled. SUPERSEDED: replaced by newer assignment.
     */
    @Builder.Default
    @Column(nullable = false, length = 50)
    private String status = "ACTIVE";

    // Rotation config (only used when assignmentType = ROTATING)
    @Column(name = "rotation_cycle_days")
    private Integer rotationCycleDays;

    @Column(name = "rotation_sequence")
    private Integer rotationSequence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    private AppUser assignedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private AppUser cancelledBy;
}

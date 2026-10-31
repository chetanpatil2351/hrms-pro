package com.hrmspro.modules.shift;

import com.hrmspro.common.base.BaseEntity;
import com.hrmspro.modules.employee.Employee;
import com.hrmspro.modules.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * ShiftAssignmentHistory: Immutable audit trail of every assignment lifecycle event.
 * Does not extend BaseTenantEntity since it duplicates tenant_id as a plain column (no updatable constraint needed).
 */
@Getter
@Setter
@Entity
@Table(name = "shift_assignment_history")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftAssignmentHistory extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id")
    private EmployeeShiftAssignment assignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "old_shift_id")
    private ShiftTemplate oldShift;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_shift_id")
    private ShiftTemplate newShift;

    /**
     * CREATED, UPDATED, CANCELLED, SUPERSEDED, BULK_CREATED
     */
    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "change_reason", columnDefinition = "TEXT")
    private String changeReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by")
    private AppUser performedBy;

    @Column(name = "effective_start")
    private LocalDate effectiveStart;

    @Column(name = "effective_end")
    private LocalDate effectiveEnd;
}

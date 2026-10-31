package com.hrmspro.modules.shift;

import com.hrmspro.common.base.BaseTenantEntity;
import com.hrmspro.modules.employee.Employee;
import com.hrmspro.modules.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * ShiftChangeRequest: Employee-initiated request to change their shift assignment.
 * Self-approval is prevented in the service layer (reviewer cannot be the requester).
 * Approval re-validates shift overlap before creating a new assignment.
 */
@Getter
@Setter
@Entity
@Table(name = "shift_change_requests")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftChangeRequest extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private AppUser requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_assignment_id")
    private EmployeeShiftAssignment currentAssignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_shift_id", nullable = false)
    private ShiftTemplate requestedShift;

    @Column(name = "requested_effective_date", nullable = false)
    private LocalDate requestedEffectiveDate;

    @Column(name = "requested_effective_end")
    private LocalDate requestedEffectiveEnd;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    /**
     * PENDING, APPROVED, REJECTED, WITHDRAWN
     */
    @Builder.Default
    @Column(nullable = false, length = 50)
    private String status = "PENDING";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private AppUser reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewer_remarks", columnDefinition = "TEXT")
    private String reviewerRemarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applied_assignment_id")
    private EmployeeShiftAssignment appliedAssignment; // set when approved and new assignment created
}

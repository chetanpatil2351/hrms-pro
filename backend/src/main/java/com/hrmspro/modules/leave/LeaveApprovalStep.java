package com.hrmspro.modules.leave;

import com.hrmspro.common.base.BaseTenantEntity;
import com.hrmspro.modules.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "leave_approval_steps")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveApprovalStep extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_application_id", nullable = false)
    private LeaveApplication leaveApplication;

    @Builder.Default
    @Column(name = "step_number", nullable = false)
    private Integer stepNumber = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_user_id")
    private AppUser approverUser;

    @Builder.Default
    @Column(nullable = false, length = 50)
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED

    @Column(columnDefinition = "TEXT")
    private String comments;

    @Column(name = "action_time")
    private Instant actionTime;
}

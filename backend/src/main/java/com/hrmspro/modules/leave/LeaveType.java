package com.hrmspro.modules.leave;

import com.hrmspro.common.base.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@Table(
    name = "leave_types",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_leave_type_tenant_code", columnNames = {"tenant_id", "code"})
    }
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveType extends BaseTenantEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String code; // CASUAL_LEAVE, SICK_LEAVE, EARNED_LEAVE, UNPAID_LEAVE, COMPENSATORY_OFF, etc.

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Column(name = "is_paid", nullable = false)
    private Boolean isPaid = true;

    @Builder.Default
    @Column(nullable = false, length = 50)
    private String category = "ANNUAL"; // ANNUAL, MEDICAL, SPECIAL, UNPAID, COMP_OFF

    @Builder.Default
    @Column(name = "is_half_day_allowed", nullable = false)
    private Boolean isHalfDayAllowed = true;

    @Builder.Default
    @Column(name = "requires_attachment", nullable = false)
    private Boolean requiresAttachment = false;

    @Builder.Default
    @Column(name = "attachment_mandatory_days", nullable = false)
    private Integer attachmentMandatoryDays = 3;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}

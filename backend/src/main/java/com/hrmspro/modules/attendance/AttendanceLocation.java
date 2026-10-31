package com.hrmspro.modules.attendance;

import com.hrmspro.common.base.BaseTenantEntity;
import com.hrmspro.modules.tenant.Branch;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@Table(name = "attendance_locations")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceLocation extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Builder.Default
    @Column(name = "radius_meters", nullable = false)
    private Integer radiusMeters = 100;

    @Builder.Default
    @Column(name = "policy_mode", nullable = false, length = 50)
    private String policyMode = "STRICT"; // STRICT, WARNING_ONLY, APPROVAL_REQUIRED

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
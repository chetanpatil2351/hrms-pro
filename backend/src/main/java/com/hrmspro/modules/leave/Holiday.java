package com.hrmspro.modules.leave;

import com.hrmspro.common.base.BaseTenantEntity;
import com.hrmspro.modules.tenant.Branch;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(
    name = "holidays",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_holiday_tenant_branch_date", columnNames = {"tenant_id", "branch_id", "holiday_date"})
    }
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Holiday extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch; // NULL = company-wide holiday

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Column(name = "holiday_type", nullable = false, length = 50)
    private String holidayType = "PUBLIC"; // PUBLIC, RESTRICTED, OPTIONAL, COMPANY

    @Builder.Default
    @Column(name = "is_recurring", nullable = false)
    private Boolean isRecurring = false;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}

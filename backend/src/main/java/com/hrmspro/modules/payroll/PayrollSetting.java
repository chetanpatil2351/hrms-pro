package com.hrmspro.modules.payroll;

import com.hrmspro.common.base.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@Table(name = "payroll_settings",
       uniqueConstraints = {@UniqueConstraint(name = "uk_payroll_settings_tenant", columnNames = {"tenant_id"})})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollSetting extends BaseTenantEntity {

    /**
     * Proration policy for payroll calculations.
     * CALENDAR_DAYS: payable days counted against calendar days (default).
     * WORKING_DAYS: payable days counted against actual working days (excludes weekends and branch holidays).
     */
    @Builder.Default
    @Column(name = "proration_policy", nullable = false, length = 20)
    private String prorationPolicy = "CALENDAR_DAYS";
}

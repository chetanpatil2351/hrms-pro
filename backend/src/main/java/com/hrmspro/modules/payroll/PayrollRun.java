package com.hrmspro.modules.payroll;

import com.hrmspro.common.base.BaseTenantEntity;
import com.hrmspro.modules.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(
    name = "payroll_runs",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_payroll_run", columnNames = {"tenant_id", "payroll_period_id", "run_type", "run_number"})
    }
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollRun extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_period_id", nullable = false)
    private PayrollPeriod payrollPeriod;

    @Builder.Default
    @Column(name = "run_type", nullable = false, length = 50)
    private String runType = "REGULAR"; // REGULAR, SUPPLEMENTARY

    @Builder.Default
    @Column(name = "run_number", nullable = false)
    private Integer runNumber = 1;

    @Builder.Default
    @Column(nullable = false, length = 50)
    private String status = "COMPLETED"; // PROCESSING, COMPLETED, FAILED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiated_by")
    private AppUser initiatedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Builder.Default
    @OneToMany(mappedBy = "payrollRun", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PayrollRunEmployee> employees = new ArrayList<>();
}

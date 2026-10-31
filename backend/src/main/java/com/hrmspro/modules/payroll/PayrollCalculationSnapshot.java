package com.hrmspro.modules.payroll;

import com.hrmspro.common.base.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@Table(name = "payroll_calculation_snapshots")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollCalculationSnapshot extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_run_employee_id", nullable = false)
    private PayrollRunEmployee payrollRunEmployee;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String snapshotJson;
}

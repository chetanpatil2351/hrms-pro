package com.hrmspro.modules.payroll;

import com.hrmspro.common.base.BaseTenantEntity;
import com.hrmspro.modules.employee.Employee;
import com.hrmspro.modules.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(
    name = "payroll_adjustments",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_payroll_adj_idemp", columnNames = {"tenant_id", "idempotency_key"})
    }
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollAdjustment extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_period_id")
    private PayrollPeriod payrollPeriod;

    @Column(name = "adjustment_type", nullable = false, length = 50)
    private String adjustmentType; // ADVANCE_DEDUCTION, LOAN_EMI, REIMBURSEMENT, BONUS, ARREARS

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount; // positive = credit/earning, negative = deduction

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Builder.Default
    @Column(name = "is_processed", nullable = false)
    private Boolean isProcessed = false;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "authorized_by")
    private AppUser authorizedBy;
}

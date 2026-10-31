package com.hrmspro.modules.leave;

import com.hrmspro.common.base.BaseTenantEntity;
import com.hrmspro.modules.employee.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(
    name = "employee_leave_balances",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_emp_leave_bal", columnNames = {"tenant_id", "employee_id", "leave_type_id", "cycle_year"})
    }
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeLeaveBalance extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "cycle_year", nullable = false)
    private Integer cycleYear;

    @Builder.Default
    @Column(name = "opening_balance", nullable = false, precision = 6, scale = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "accrued_amount", nullable = false, precision = 6, scale = 2)
    private BigDecimal accruedAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "credited_amount", nullable = false, precision = 6, scale = 2)
    private BigDecimal creditedAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "used_amount", nullable = false, precision = 6, scale = 2)
    private BigDecimal usedAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "pending_approval_amount", nullable = false, precision = 6, scale = 2)
    private BigDecimal pendingApprovalAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "expired_amount", nullable = false, precision = 6, scale = 2)
    private BigDecimal expiredAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "carried_forward_amount", nullable = false, precision = 6, scale = 2)
    private BigDecimal carriedForwardAmount = BigDecimal.ZERO;

    public BigDecimal getAvailableBalance() {
        return openingBalance.add(accruedAmount)
                .add(creditedAmount)
                .add(carriedForwardAmount)
                .subtract(usedAmount)
                .subtract(expiredAmount)
                .subtract(pendingApprovalAmount);
    }
}

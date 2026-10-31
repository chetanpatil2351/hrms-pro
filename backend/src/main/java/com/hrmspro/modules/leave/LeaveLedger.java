package com.hrmspro.modules.leave;

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
    name = "leave_ledger",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_leave_ledger_idempotency", columnNames = {"tenant_id", "idempotency_key"})
    }
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveLedger extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_application_id")
    private LeaveApplication leaveApplication;

    @Column(name = "cycle_year", nullable = false)
    private Integer cycleYear;

    @Column(name = "transaction_type", nullable = false, length = 50)
    private String transactionType; // ALLOCATION, ACCRUAL, DEBIT, CREDIT_REVERSAL, MANUAL_ADJUSTMENT, EXPIRY, CARRY_FORWARD

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 6, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;
}

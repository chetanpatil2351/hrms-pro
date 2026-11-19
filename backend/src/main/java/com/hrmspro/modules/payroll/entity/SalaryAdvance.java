package com.hrmspro.modules.payroll.entity;

import com.hrmspro.common.base.BaseTenantEntity;
import com.hrmspro.modules.employee.Employee;
import com.hrmspro.modules.user.AppUser;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Salary advance request and tracking entity.
 *
 * An employee can request a cash advance which must be approved by a manager/HR.
 * Once approved, the advance is repaid via payroll deductions in equal installments.
 * The entity stores the full repayment schedule and the outstanding balance.
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "salary_advances",
        indexes = {@Index(name = "idx_salary_advance_tenant", columnList = "tenant_id"),
                   @Index(name = "idx_salary_advance_employee", columnList = "employee_id"),
                   @Index(name = "idx_salary_advance_status", columnList = "status")})
public class SalaryAdvance extends BaseTenantEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /**
     * Amount the employee originally requested.
     */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal requestedAmount;

    /**
     * Amount that was approved (may differ from requested).
     */
    @Column(precision = 15, scale = 2)
    private BigDecimal approvedAmount;

    /**
     * Current status of the advance.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SalaryAdvanceStatus status;

    /**
     * When the request was created.
     */
    @Column(nullable = false)
    private Instant requestDate;

    /**
     * User who approved or rejected the request.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private AppUser approvedBy;

    /**
     * Timestamp of approval/rejection.
     */
    private Instant decisionDate;

    /**
     * Date from which repayment installments start.
     */
    private LocalDate repaymentStartDate;

    /**
     * Number of equal installments for repayment.
     */
    @ColumnDefault("0")
    private Integer installmentCount;

    /**
     * Amount of each installment (calculated after approval).
     */
    @Column(precision = 15, scale = 2)
    private BigDecimal installmentAmount;

    /**
     * Remaining amount to be recovered.
     */
    @Column(precision = 15, scale = 2)
    private BigDecimal outstandingBalance;

    /**
     * Optional free‑form notes.
     */
    @Column(columnDefinition = "TEXT")
    private String notes;
}

package com.hrmspro.modules.payroll.entity;

import com.hrmspro.common.base.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Records a single repayment (installment) of a {@link SalaryAdvance} that was deducted
 * during a specific {@link PayrollRun}. This entity provides an immutable audit trail
 * linking the advance, the payroll run, the amount deducted and the installment number.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "salary_advance_repayments",
       uniqueConstraints = {@UniqueConstraint(name = "uk_advance_run", columnNames = {"advance_id", "payroll_run_id"})},
       indexes = {
           @Index(name = "idx_sa_rep_tenant", columnList = "tenant_id"),
           @Index(name = "idx_sa_rep_advance", columnList = "advance_id"),
           @Index(name = "idx_sa_rep_run", columnList = "payroll_run_id")
       })
public class SalaryAdvanceRepayment extends BaseTenantEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "advance_id", nullable = false)
    private SalaryAdvance advance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_run_id", nullable = false)
    private PayrollRun payrollRun;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "installment_number")
    private Integer installmentNumber;

    @Column(name = "deduction_date")
    private LocalDate deductionDate;
}

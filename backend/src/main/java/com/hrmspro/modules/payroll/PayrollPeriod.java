package com.hrmspro.modules.payroll;

import com.hrmspro.common.base.BaseTenantEntity;
import com.hrmspro.modules.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(
    name = "payroll_periods",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_payroll_period", columnNames = {"tenant_id", "period_year", "period_month"})
    }
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollPeriod extends BaseTenantEntity {

    @Column(name = "period_year", nullable = false)
    private Integer year;

    @Column(name = "period_month", nullable = false)
    private Integer month;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Builder.Default
    @Column(nullable = false, length = 50)
    private String status = "DRAFT"; // DRAFT, PROCESSING, REVIEW, APPROVED, FINALIZED, PAID

    @Builder.Default
    @Column(name = "total_employees", nullable = false)
    private Integer totalEmployees = 0;

    @Builder.Default
    @Column(name = "total_gross_pay", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalGrossPay = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_net_pay", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalNetPay = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_employer_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalEmployerCost = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "finalized_by")
    private AppUser finalizedBy;

    @Column(name = "finalized_at")
    private Instant finalizedAt;
}

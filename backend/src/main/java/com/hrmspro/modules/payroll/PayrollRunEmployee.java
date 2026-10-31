package com.hrmspro.modules.payroll;

import com.hrmspro.common.base.BaseTenantEntity;
import com.hrmspro.modules.employee.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(
    name = "payroll_run_employees",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_run_emp", columnNames = {"payroll_run_id", "employee_id"})
    }
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollRunEmployee extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_run_id", nullable = false)
    private PayrollRun payrollRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "total_days", nullable = false)
    private Integer totalDays;

    @Column(name = "payable_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal payableDays;

    @Builder.Default
    @Column(name = "unpaid_leave_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal unpaidLeaveDays = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "worked_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal workedDays = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "holidays_and_weekly_offs", nullable = false, precision = 5, scale = 2)
    private BigDecimal holidaysAndWeeklyOffs = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "gross_earnings", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossEarnings = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_deductions", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "employer_contributions", nullable = false, precision = 14, scale = 2)
    private BigDecimal employerContributions = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "net_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal netSalary = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "is_frozen", nullable = false)
    private Boolean isFrozen = false;

    @Builder.Default
    @OneToMany(mappedBy = "payrollRunEmployee", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PayrollLineItem> lineItems = new ArrayList<>();
}

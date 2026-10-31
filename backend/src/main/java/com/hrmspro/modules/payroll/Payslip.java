package com.hrmspro.modules.payroll;

import com.hrmspro.common.base.BaseTenantEntity;
import com.hrmspro.modules.employee.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(
    name = "payslips",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_payslip_num", columnNames = {"tenant_id", "payslip_number"})
    }
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payslip extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_run_employee_id", nullable = false)
    private PayrollRunEmployee payrollRunEmployee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_period_id", nullable = false)
    private PayrollPeriod payrollPeriod;

    @Column(name = "payslip_number", nullable = false, length = 100)
    private String payslipNumber;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "gross_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossSalary;

    @Column(name = "total_deductions", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDeductions;

    @Column(name = "net_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal netSalary;

    @Builder.Default
    @Column(name = "payment_status", nullable = false, length = 50)
    private String paymentStatus = "UNPAID"; // UNPAID, PAID, HOLD

    @Column(name = "payslip_data_json", nullable = false, columnDefinition = "TEXT")
    private String payslipDataJson;
}

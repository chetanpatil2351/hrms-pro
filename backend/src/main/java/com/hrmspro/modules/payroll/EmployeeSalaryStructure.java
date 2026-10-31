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
@Table(name = "employee_salary_structures")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeSalaryStructure extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "salary_structure_id", nullable = false)
    private SalaryStructure salaryStructure;

    @Builder.Default
    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "annual_ctc", nullable = false, precision = 14, scale = 2)
    private BigDecimal annualCtc;

    @Column(name = "monthly_gross", nullable = false, precision = 14, scale = 2)
    private BigDecimal monthlyGross;

    @Column(name = "monthly_base_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal monthlyBaseSalary;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}

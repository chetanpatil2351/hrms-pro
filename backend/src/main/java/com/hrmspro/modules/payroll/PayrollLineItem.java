package com.hrmspro.modules.payroll;

import com.hrmspro.common.base.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "payroll_line_items")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollLineItem extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_run_employee_id", nullable = false)
    private PayrollRunEmployee payrollRunEmployee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salary_component_id")
    private SalaryComponent salaryComponent;

    @Column(name = "component_name", nullable = false, length = 100)
    private String componentName;

    @Column(name = "component_code", nullable = false, length = 50)
    private String componentCode;

    @Column(nullable = false, length = 30)
    private String type; // EARNING, DEDUCTION, EMPLOYER_CONTRIBUTION

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
}

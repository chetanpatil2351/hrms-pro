package com.hrmspro.modules.payroll;

import com.hrmspro.common.base.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(
    name = "salary_components",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_salary_comp_tenant_code", columnNames = {"tenant_id", "code"})
    }
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryComponent extends BaseTenantEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 30)
    private String type; // EARNING, DEDUCTION, EMPLOYER_CONTRIBUTION

    @Builder.Default
    @Column(name = "calculation_type", nullable = false, length = 20)
    private String calculationType = "FIXED_AMOUNT"; // FIXED_AMOUNT, PERCENTAGE

    @Column(name = "calculation_base", length = 50)
    private String calculationBase; // BASIC, GROSS, etc.

    @Column(name = "percentage_rate", precision = 6, scale = 4)
    private BigDecimal percentageRate;

    @Builder.Default
    @Column(name = "is_taxable", nullable = false)
    private Boolean isTaxable = true;

    @Builder.Default
    @Column(name = "include_in_gross", nullable = false)
    private Boolean includeInGross = true;

    @Builder.Default
    @Column(name = "include_in_ctc", nullable = false)
    private Boolean includeInCTC = true;

    @Builder.Default
    @Column(name = "is_statutory", nullable = false)
    private Boolean isStatutory = false;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}

package com.hrmspro.modules.payroll;

import com.hrmspro.common.base.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(
    name = "statutory_rules",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_statutory_rule", columnNames = {"tenant_id", "rule_type", "state", "effective_year"})
    }
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatutoryRule extends BaseTenantEntity {

    @Column(name = "rule_type", nullable = false, length = 50)
    private String ruleType; // EPF, ESI, PROFESSIONAL_TAX, TDS

    @Column(length = 50)
    private String state; // e.g. "MAHARASHTRA", "KARNATAKA", or null for nationwide

    @Column(name = "effective_year", nullable = false)
    private Integer effectiveYear;

    @Builder.Default
    @Column(name = "employee_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal employeeRate = BigDecimal.ZERO; // e.g. 0.1200 for 12%, or fixed flat amount for PT like 200.0000

    @Builder.Default
    @Column(name = "employer_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal employerRate = BigDecimal.ZERO; // e.g. 0.1200 for 12%

    @Column(name = "wage_ceiling", precision = 12, scale = 2)
    private BigDecimal wageCeiling; // 15000 for EPF, 21000 for ESI

    @Builder.Default
    @Column(name = "minimum_wage_threshold", precision = 12, scale = 2)
    private BigDecimal minimumWageThreshold = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}

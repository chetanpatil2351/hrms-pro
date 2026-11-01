package com.hrmspro.modules.payroll;

import com.hrmspro.common.base.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(
    name = "statutory_rules",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_statutory_rule", columnNames = {"tenant_id", "rule_type", "state", "effective_from", "jurisdiction"})
    }
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatutoryRule extends BaseTenantEntity {

    @Column(name = "rule_type", nullable = false, length = 50)
    private String ruleType; // EPF, ESI, PROFESSIONAL_TAX, TDS

    @Column(length = 50)
    private String state; // null for nationwide

    @Column(name = "effective_year", nullable = false)
    private Integer effectiveYear;

    @Builder.Default
    @Column(name = "employee_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal employeeRate = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "employer_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal employerRate = BigDecimal.ZERO;

    @Column(name = "wage_ceiling", precision = 12, scale = 2)
    private BigDecimal wageCeiling;

    @Builder.Default
    @Column(name = "minimum_wage_threshold", precision = 12, scale = 2)
    private BigDecimal minimumWageThreshold = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    // Effective-dated fields added in V9
    @Builder.Default
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom = LocalDate.of(1970, 1, 1);

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Builder.Default
    @Column(name = "jurisdiction", length = 50, nullable = false)
    private String jurisdiction = "ALL";

    @Column(name = "source_reference", length = 255)
    private String sourceReference;

    @Column(name = "rule_version", length = 20)
    private String ruleVersion;

    @Column(name = "disability_ceiling", precision = 12, scale = 2)
    private BigDecimal disabilityCeiling;
}

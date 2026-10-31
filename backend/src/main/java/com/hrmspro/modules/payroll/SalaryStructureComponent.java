package com.hrmspro.modules.payroll;

import com.hrmspro.common.base.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(
    name = "salary_structure_components",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_struct_comp", columnNames = {"salary_structure_id", "salary_component_id"})
    }
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryStructureComponent extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "salary_structure_id", nullable = false)
    private SalaryStructure salaryStructure;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "salary_component_id", nullable = false)
    private SalaryComponent salaryComponent;

    @Builder.Default
    @Column(name = "default_amount", precision = 12, scale = 2)
    private BigDecimal defaultAmount = BigDecimal.ZERO;

    @Column(name = "percentage_rate", precision = 6, scale = 4)
    private BigDecimal percentageRate;

    @Builder.Default
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 1;
}

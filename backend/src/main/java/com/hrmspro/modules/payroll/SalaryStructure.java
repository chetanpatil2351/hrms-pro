package com.hrmspro.modules.payroll;

import com.hrmspro.common.base.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(
    name = "salary_structures",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_salary_struct_tenant_code", columnNames = {"tenant_id", "code"})
    }
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryStructure extends BaseTenantEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Builder.Default
    @OneToMany(mappedBy = "salaryStructure", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SalaryStructureComponent> components = new ArrayList<>();
}

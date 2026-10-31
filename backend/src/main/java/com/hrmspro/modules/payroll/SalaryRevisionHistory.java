package com.hrmspro.modules.payroll;

import com.hrmspro.common.base.BaseTenantEntity;
import com.hrmspro.modules.employee.Employee;
import com.hrmspro.modules.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "salary_revision_history")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryRevisionHistory extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_structure_id")
    private SalaryStructure previousStructure;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "new_structure_id", nullable = false)
    private SalaryStructure newStructure;

    @Column(name = "previous_annual_ctc", precision = 14, scale = 2)
    private BigDecimal previousAnnualCtc;

    @Column(name = "new_annual_ctc", nullable = false, precision = 14, scale = 2)
    private BigDecimal newAnnualCtc;

    @Column(name = "revision_date", nullable = false)
    private LocalDate revisionDate;

    @Column(name = "revision_reason", columnDefinition = "TEXT")
    private String revisionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revised_by")
    private AppUser revisedBy;
}

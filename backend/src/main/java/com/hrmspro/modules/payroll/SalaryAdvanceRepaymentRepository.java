package com.hrmspro.modules.payroll;

import com.hrmspro.modules.payroll.entity.SalaryAdvanceRepayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link SalaryAdvanceRepayment}.
 * All queries are tenant‑scoped; the project does not use automatic row‑level security.
 */
@Repository
public interface SalaryAdvanceRepaymentRepository extends JpaRepository<SalaryAdvanceRepayment, UUID> {

    /**
     * Find all repayments for a specific salary advance within a tenant.
     */
    List<SalaryAdvanceRepayment> findByAdvance_IdAndTenantId(UUID advanceId, UUID tenantId);

    /**
     * Find all repayments that belong to a particular payroll run within a tenant.
     */
    List<SalaryAdvanceRepayment> findByPayrollRun_IdAndTenantId(UUID payrollRunId, UUID tenantId);

    /**
     * Check whether a repayment entry already exists for the given advance and payroll run.
     * Used to avoid duplicate deductions in the same payroll run.
     */
    boolean existsByAdvance_IdAndPayrollRun_Id(UUID advanceId, UUID payrollRunId);
}

package com.hrmspro.modules.payroll;

import com.hrmspro.modules.payroll.entity.SalaryAdvance;
import com.hrmspro.modules.payroll.entity.SalaryAdvanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link SalaryAdvance} with tenant‑scoped query methods.
 *
 * The project uses explicit tenant filtering (via {@code tenantId}) rather than
 * relying on PostgreSQL row‑level security alone, so each query includes the
 * tenant identifier.
 */
@Repository
public interface SalaryAdvanceRepository extends JpaRepository<SalaryAdvance, UUID> {

    /**
     * Find a salary advance by its primary key and tenant.
     */
    Optional<SalaryAdvance> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Find all advances for a given employee within a tenant.
     */
    List<SalaryAdvance> findByEmployee_IdAndTenantId(UUID employeeId, UUID tenantId);

    /**
     * Retrieve all advances belonging to a tenant.
     */
    List<SalaryAdvance> findByTenantId(UUID tenantId);

    /**
     * Find advances with a specific status within a tenant.
     */
    List<SalaryAdvance> findByStatusAndTenantId(SalaryAdvanceStatus status, UUID tenantId);

    /**
     * Find advances that are approved or active and still have an outstanding balance.
     * This is used by the payroll engine to recover amounts.
     */
    @Query("SELECT s FROM SalaryAdvance s " +
            "WHERE s.tenantId = :tenantId " +
            "AND (s.status = com.hrmspro.modules.payroll.entity.SalaryAdvanceStatus.APPROVED " +
            "OR s.status = com.hrmspro.modules.payroll.entity.SalaryAdvanceStatus.ACTIVE) " +
            "AND s.outstandingBalance > 0")
    List<SalaryAdvance> findEligibleForPayrollRecovery(@Param("tenantId") UUID tenantId);
}

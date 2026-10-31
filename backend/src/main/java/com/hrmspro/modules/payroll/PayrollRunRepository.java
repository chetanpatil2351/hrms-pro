package com.hrmspro.modules.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayrollRunRepository extends JpaRepository<PayrollRun, UUID> {
    Optional<PayrollRun> findByIdAndTenantId(UUID id, UUID tenantId);
    List<PayrollRun> findByPayrollPeriodIdOrderByRunNumberDesc(UUID payrollPeriodId);

    @Query("SELECT COALESCE(MAX(r.runNumber), 0) FROM PayrollRun r WHERE r.tenantId = :tenantId AND r.payrollPeriod.id = :periodId")
    Integer findMaxRunNumber(@Param("tenantId") UUID tenantId, @Param("periodId") UUID periodId);
}

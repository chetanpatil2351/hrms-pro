package com.hrmspro.modules.payroll;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayrollAdjustmentRepository extends JpaRepository<PayrollAdjustment, UUID> {
    Optional<PayrollAdjustment> findByIdAndTenantId(UUID id, UUID tenantId);
    List<PayrollAdjustment> findByTenantIdAndEmployeeIdAndIsProcessedFalse(UUID tenantId, UUID employeeId);
    List<PayrollAdjustment> findByPayrollPeriodId(UUID payrollPeriodId);
    boolean existsByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
    Page<PayrollAdjustment> findByTenantId(UUID tenantId, Pageable pageable);
}

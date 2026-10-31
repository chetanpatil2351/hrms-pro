package com.hrmspro.modules.payroll;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayrollPeriodRepository extends JpaRepository<PayrollPeriod, UUID> {
    Optional<PayrollPeriod> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<PayrollPeriod> findByTenantIdAndYearAndMonth(UUID tenantId, Integer year, Integer month);
    boolean existsByTenantIdAndYearAndMonth(UUID tenantId, Integer year, Integer month);
    Page<PayrollPeriod> findByTenantIdOrderByYearDescMonthDesc(UUID tenantId, Pageable pageable);
}

package com.hrmspro.modules.payroll;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SalaryComponentRepository extends JpaRepository<SalaryComponent, UUID> {
    Optional<SalaryComponent> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<SalaryComponent> findByTenantIdAndCode(UUID tenantId, String code);
    boolean existsByTenantIdAndCode(UUID tenantId, String code);
    List<SalaryComponent> findByTenantIdAndIsActiveTrue(UUID tenantId);
    Page<SalaryComponent> findByTenantId(UUID tenantId, Pageable pageable);
}

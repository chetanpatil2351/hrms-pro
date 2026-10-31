package com.hrmspro.modules.payroll;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SalaryStructureRepository extends JpaRepository<SalaryStructure, UUID> {
    Optional<SalaryStructure> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<SalaryStructure> findByTenantIdAndCode(UUID tenantId, String code);
    boolean existsByTenantIdAndCode(UUID tenantId, String code);
    List<SalaryStructure> findByTenantIdAndIsActiveTrue(UUID tenantId);
    Page<SalaryStructure> findByTenantId(UUID tenantId, Pageable pageable);
}

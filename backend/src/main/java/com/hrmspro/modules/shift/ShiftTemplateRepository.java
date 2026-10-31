package com.hrmspro.modules.shift;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShiftTemplateRepository extends JpaRepository<ShiftTemplate, UUID> {

    List<ShiftTemplate> findByTenantIdAndIsActiveTrue(UUID tenantId);

    Page<ShiftTemplate> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<ShiftTemplate> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    /**
     * Detect duplicate shift code within tenant, excluding the given id (for update operations).
     */
    @Query("SELECT COUNT(s) > 0 FROM ShiftTemplate s WHERE s.tenantId = :tenantId AND s.code = :code AND s.id <> :excludeId")
    boolean existsByTenantIdAndCodeAndIdNot(@Param("tenantId") UUID tenantId,
                                             @Param("code") String code,
                                             @Param("excludeId") UUID excludeId);

    List<ShiftTemplate> findByTenantIdAndBranchIdAndIsActiveTrue(UUID tenantId, UUID branchId);
}

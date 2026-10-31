package com.hrmspro.modules.leave;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeaveTypeRepository extends JpaRepository<LeaveType, UUID> {

    Optional<LeaveType> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<LeaveType> findByTenantIdAndCode(UUID tenantId, String code);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    List<LeaveType> findByTenantIdAndIsActiveTrue(UUID tenantId);

    Page<LeaveType> findByTenantId(UUID tenantId, Pageable pageable);
}

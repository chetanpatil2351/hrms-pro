package com.hrmspro.modules.shift;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShiftChangeRequestRepository extends JpaRepository<ShiftChangeRequest, UUID> {

    Optional<ShiftChangeRequest> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ShiftChangeRequest> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(UUID tenantId, UUID employeeId);

    Page<ShiftChangeRequest> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    List<ShiftChangeRequest> findByTenantIdAndStatusOrderByCreatedAtAsc(UUID tenantId, String status);
}

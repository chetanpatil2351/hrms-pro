package com.hrmspro.modules.leave;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeaveLedgerRepository extends JpaRepository<LeaveLedger, UUID> {

    List<LeaveLedger> findByTenantIdAndEmployeeIdAndCycleYearOrderByCreatedAtDesc(
            UUID tenantId, UUID employeeId, Integer cycleYear);

    Page<LeaveLedger> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(
            UUID tenantId, UUID employeeId, Pageable pageable);

    Optional<LeaveLedger> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    boolean existsByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
}

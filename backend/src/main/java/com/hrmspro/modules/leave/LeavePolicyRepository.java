package com.hrmspro.modules.leave;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeavePolicyRepository extends JpaRepository<LeavePolicy, UUID> {

    Optional<LeavePolicy> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<LeavePolicy> findByTenantIdAndLeaveTypeIdAndPolicyEffectiveYear(UUID tenantId, UUID leaveTypeId, Integer year);

    List<LeavePolicy> findByTenantIdAndPolicyEffectiveYearAndIsActiveTrue(UUID tenantId, Integer year);

    List<LeavePolicy> findByTenantIdAndIsActiveTrue(UUID tenantId);
}

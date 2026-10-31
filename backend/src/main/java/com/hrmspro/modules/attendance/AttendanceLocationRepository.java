package com.hrmspro.modules.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AttendanceLocationRepository extends JpaRepository<AttendanceLocation, UUID> {
    List<AttendanceLocation> findByTenantIdAndIsActiveTrue(UUID tenantId);
    List<AttendanceLocation> findByTenantIdAndBranchIdAndIsActiveTrue(UUID tenantId, UUID branchId);
}
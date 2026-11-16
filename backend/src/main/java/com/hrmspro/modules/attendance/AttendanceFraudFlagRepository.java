package com.hrmspro.modules.attendance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceFraudFlagRepository extends JpaRepository<AttendanceFraudFlag, UUID> {

    Optional<AttendanceFraudFlag> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<AttendanceFraudFlag> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Page<AttendanceFraudFlag> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(UUID tenantId, UUID employeeId, Pageable pageable);

    @Query("""
        SELECT f FROM AttendanceFraudFlag f
        WHERE f.tenantId = :tenantId
        AND (:employeeId IS NULL OR f.employee.id = :employeeId)
        AND (:status IS NULL OR f.status = :status)
        AND (:flagType IS NULL OR f.flagType = :flagType)
        ORDER BY f.createdAt DESC
    """)
    Page<AttendanceFraudFlag> findWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("status") String status,
            @Param("flagType") String flagType,
            Pageable pageable
    );
}

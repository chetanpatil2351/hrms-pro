package com.hrmspro.modules.attendance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AttendanceCorrectionRepository extends JpaRepository<AttendanceCorrection, UUID> {
    Page<AttendanceCorrection> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status, Pageable pageable);
    Page<AttendanceCorrection> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(UUID tenantId, UUID employeeId, Pageable pageable);
}
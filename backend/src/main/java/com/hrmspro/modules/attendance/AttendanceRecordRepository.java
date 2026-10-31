package com.hrmspro.modules.attendance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {

    Optional<AttendanceRecord> findByTenantIdAndEmployeeIdAndAttendanceDateAndSessionNumber(UUID tenantId, UUID employeeId, LocalDate attendanceDate, Integer sessionNumber);

    Optional<AttendanceRecord> findByTenantIdAndEmployeeIdAndAttendanceDate(UUID tenantId, UUID employeeId, LocalDate attendanceDate);

    Optional<AttendanceRecord> findFirstByTenantIdAndEmployeeIdAndStatusOrderByCheckInTimeDesc(UUID tenantId, UUID employeeId, String status);

    @Query("SELECT COALESCE(MAX(a.sessionNumber), 0) FROM AttendanceRecord a WHERE a.tenantId = :tenantId AND a.employee.id = :employeeId AND a.attendanceDate = :attendanceDate")
    Integer findMaxSessionNumber(@Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId, @Param("attendanceDate") LocalDate attendanceDate);

    Page<AttendanceRecord> findByTenantIdAndEmployeeIdOrderByAttendanceDateDesc(UUID tenantId, UUID employeeId, Pageable pageable);

    @Query("""
        SELECT a FROM AttendanceRecord a
        WHERE a.tenantId = :tenantId
        AND (:branchId IS NULL OR a.branch.id = :branchId)
        AND (:employeeId IS NULL OR a.employee.id = :employeeId)
        AND (:startDate IS NULL OR a.attendanceDate >= :startDate)
        AND (:endDate IS NULL OR a.attendanceDate <= :endDate)
        ORDER BY a.attendanceDate DESC, a.checkInTime DESC
    """)
    Page<AttendanceRecord> findAttendanceWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("branchId") UUID branchId,
            @Param("employeeId") UUID employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable
    );
}
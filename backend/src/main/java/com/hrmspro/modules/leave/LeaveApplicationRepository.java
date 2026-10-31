package com.hrmspro.modules.leave;

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
public interface LeaveApplicationRepository extends JpaRepository<LeaveApplication, UUID> {

    Optional<LeaveApplication> findByIdAndTenantId(UUID id, UUID tenantId);

    List<LeaveApplication> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(UUID tenantId, UUID employeeId);

    Page<LeaveApplication> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    @Query("""
        SELECT COUNT(a) > 0 FROM LeaveApplication a
        WHERE a.employee.id = :employeeId
          AND a.status IN ('PENDING', 'APPROVED')
          AND a.startDate <= :endDate
          AND a.endDate >= :startDate
        """)
    boolean hasOverlappingLeave(
            @Param("employeeId") UUID employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
        SELECT a FROM LeaveApplication a
        WHERE a.tenantId = :tenantId
          AND (:employeeId IS NULL OR a.employee.id = :employeeId)
          AND (:branchId IS NULL OR a.employee.branch.id = :branchId)
          AND (:departmentId IS NULL OR a.employee.department.id = :departmentId)
          AND (:leaveTypeId IS NULL OR a.leaveType.id = :leaveTypeId)
          AND (:status IS NULL OR a.status = :status)
          AND (:startDate IS NULL OR a.endDate >= :startDate)
          AND (:endDate IS NULL OR a.startDate <= :endDate)
        ORDER BY a.startDate DESC
        """)
    Page<LeaveApplication> findLeaveReports(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("branchId") UUID branchId,
            @Param("departmentId") UUID departmentId,
            @Param("leaveTypeId") UUID leaveTypeId,
            @Param("status") String status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    @Query("""
        SELECT a FROM LeaveApplication a
        WHERE a.tenantId = :tenantId
          AND a.employee.id = :employeeId
          AND a.status = 'APPROVED'
          AND :date BETWEEN a.startDate AND a.endDate
        """)
    List<LeaveApplication> findApprovedLeaveOnDate(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("date") LocalDate date);
}

package com.hrmspro.modules.payroll;

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
public interface EmployeeSalaryStructureRepository extends JpaRepository<EmployeeSalaryStructure, UUID> {

    Optional<EmployeeSalaryStructure> findByIdAndTenantId(UUID id, UUID tenantId);

    List<EmployeeSalaryStructure> findByTenantIdAndEmployeeIdOrderByEffectiveFromDesc(UUID tenantId, UUID employeeId);

    @Query("""
        SELECT e FROM EmployeeSalaryStructure e
        WHERE e.tenantId = :tenantId
          AND e.employee.id = :employeeId
          AND e.isActive = true
          AND e.effectiveFrom <= :asOfDate
          AND (e.effectiveTo IS NULL OR e.effectiveTo >= :asOfDate)
        ORDER BY e.version DESC
    """)
    List<EmployeeSalaryStructure> findActiveAssignmentsAsOf(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("asOfDate") LocalDate asOfDate
    );

    @Query("""
        SELECT COUNT(e) > 0 FROM EmployeeSalaryStructure e
        WHERE e.tenantId = :tenantId
          AND e.employee.id = :employeeId
          AND (:excludeId IS NULL OR e.id != :excludeId)
          AND (:effectiveTo IS NULL OR e.effectiveFrom <= :effectiveTo)
          AND (e.effectiveTo IS NULL OR e.effectiveTo >= :effectiveFrom)
    """)
    boolean hasOverlappingAssignment(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("effectiveFrom") LocalDate effectiveFrom,
            @Param("effectiveTo") LocalDate effectiveTo,
            @Param("excludeId") UUID excludeId
    );

    @Query("""
        SELECT COALESCE(MAX(e.version), 0) FROM EmployeeSalaryStructure e
        WHERE e.tenantId = :tenantId
          AND e.employee.id = :employeeId
    """)
    Integer findLatestVersion(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId
    );

    Page<EmployeeSalaryStructure> findByTenantId(UUID tenantId, Pageable pageable);
}

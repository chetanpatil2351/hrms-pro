package com.hrmspro.modules.shift;

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
public interface EmployeeShiftAssignmentRepository extends JpaRepository<EmployeeShiftAssignment, UUID> {

    Optional<EmployeeShiftAssignment> findByIdAndTenantId(UUID id, UUID tenantId);

    List<EmployeeShiftAssignment> findByEmployeeIdAndStatus(UUID employeeId, String status);

    Page<EmployeeShiftAssignment> findByTenantId(UUID tenantId, Pageable pageable);

    /**
     * Interval overlap detection for a new assignment.
     * Two intervals [s1,e1] and [s2,e2] overlap when:
     * - Case 1: new assignment is bounded (effectiveEnd is not null) -> a.effectiveStart <= :effectiveEnd AND (a.effectiveEnd IS NULL OR a.effectiveEnd >= :effectiveStart)
     * - Case 2: new assignment is open-ended (effectiveEnd is null) -> (a.effectiveEnd IS NULL OR a.effectiveEnd >= :effectiveStart)
     */
    @Query("""
        SELECT COUNT(a) > 0 FROM EmployeeShiftAssignment a
        WHERE a.employee.id = :employeeId
          AND a.status = 'ACTIVE'
          AND (:effectiveEnd IS NULL OR a.effectiveStart <= :effectiveEnd)
          AND (a.effectiveEnd IS NULL OR a.effectiveEnd >= :effectiveStart)
        """)
    boolean hasOverlappingActiveAssignment(
            @Param("employeeId") UUID employeeId,
            @Param("effectiveStart") LocalDate effectiveStart,
            @Param("effectiveEnd") LocalDate effectiveEnd);

    /**
     * Same as above but excludes a specific assignment by ID (for update operations).
     */
    @Query("""
        SELECT COUNT(a) > 0 FROM EmployeeShiftAssignment a
        WHERE a.employee.id = :employeeId
          AND a.status = 'ACTIVE'
          AND a.id <> :excludeId
          AND (:effectiveEnd IS NULL OR a.effectiveStart <= :effectiveEnd)
          AND (a.effectiveEnd IS NULL OR a.effectiveEnd >= :effectiveStart)
        """)
    boolean hasOverlappingActiveAssignmentExcluding(
            @Param("employeeId") UUID employeeId,
            @Param("effectiveStart") LocalDate effectiveStart,
            @Param("effectiveEnd") LocalDate effectiveEnd,
            @Param("excludeId") UUID excludeId);

    /**
     * Resolve the effective shift assignment for an employee on a specific date.
     * Returns the ACTIVE assignment whose date range covers the given date.
     */
    @Query("""
        SELECT a FROM EmployeeShiftAssignment a
        WHERE a.employee.id = :employeeId
          AND a.status = 'ACTIVE'
          AND a.effectiveStart <= :date
          AND (a.effectiveEnd IS NULL OR a.effectiveEnd >= :date)
        ORDER BY a.effectiveStart DESC
        """)
    List<EmployeeShiftAssignment> findEffectiveAssignmentsForDate(
            @Param("employeeId") UUID employeeId,
            @Param("date") LocalDate date);

    List<EmployeeShiftAssignment> findByTenantIdAndEmployeeIdOrderByEffectiveStartDesc(UUID tenantId, UUID employeeId);
}

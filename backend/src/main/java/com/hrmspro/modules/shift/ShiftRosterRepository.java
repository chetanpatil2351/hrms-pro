package com.hrmspro.modules.shift;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShiftRosterRepository extends JpaRepository<ShiftRoster, UUID> {

    Optional<ShiftRoster> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<ShiftRoster> findByTenantIdAndEmployeeIdAndRosterDate(UUID tenantId, UUID employeeId, LocalDate rosterDate);

    List<ShiftRoster> findByTenantIdAndEmployeeIdAndRosterDateBetweenOrderByRosterDateAsc(
            UUID tenantId, UUID employeeId, LocalDate from, LocalDate to);

    List<ShiftRoster> findByTenantIdAndRosterDateBetweenAndStatusOrderByRosterDateAsc(
            UUID tenantId, LocalDate from, LocalDate to, String status);

    @Query("""
        SELECT r FROM ShiftRoster r
        WHERE r.tenantId = :tenantId
          AND r.rosterDate BETWEEN :from AND :to
          AND r.employee.branch.id = :branchId
        ORDER BY r.rosterDate ASC, r.employee.id ASC
        """)
    List<ShiftRoster> findByTenantIdAndBranchAndDateRange(
            @Param("tenantId") UUID tenantId,
            @Param("branchId") UUID branchId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    List<ShiftRoster> findByTenantIdAndEmployeeIdAndStatus(UUID tenantId, UUID employeeId, String status);

    @Query("SELECT COUNT(r) FROM ShiftRoster r WHERE r.tenantId = :tenantId AND r.rosterDate BETWEEN :from AND :to AND r.employee.id = :employeeId AND r.status = 'PUBLISHED'")
    long countPublishedRostersForEmployee(@Param("tenantId") UUID tenantId,
                                          @Param("employeeId") UUID employeeId,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);
}

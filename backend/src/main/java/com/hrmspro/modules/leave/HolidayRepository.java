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
public interface HolidayRepository extends JpaRepository<Holiday, UUID> {

    Optional<Holiday> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Holiday> findByTenantIdAndIsActiveTrueOrderByHolidayDateAsc(UUID tenantId);

    @Query("""
        SELECT h FROM Holiday h
        WHERE h.tenantId = :tenantId
          AND h.isActive = true
          AND (h.branch.id IS NULL OR h.branch.id = :branchId)
          AND h.holidayDate BETWEEN :startDate AND :endDate
        ORDER BY h.holidayDate ASC
        """)
    List<Holiday> findHolidaysForBranchAndDateRange(
            @Param("tenantId") UUID tenantId,
            @Param("branchId") UUID branchId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    Page<Holiday> findByTenantId(UUID tenantId, Pageable pageable);
}

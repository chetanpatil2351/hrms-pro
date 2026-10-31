package com.hrmspro.modules.payroll;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SalaryRevisionHistoryRepository extends JpaRepository<SalaryRevisionHistory, UUID> {
    List<SalaryRevisionHistory> findByTenantIdAndEmployeeIdOrderByRevisionDateDesc(UUID tenantId, UUID employeeId);
    Page<SalaryRevisionHistory> findByTenantId(UUID tenantId, Pageable pageable);
}

package com.hrmspro.modules.employee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EmployeeAuditLogRepository extends JpaRepository<EmployeeAuditLog, UUID> {
    List<EmployeeAuditLog> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(UUID tenantId, UUID employeeId);
}
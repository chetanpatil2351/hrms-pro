package com.hrmspro.modules.employee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, UUID> {
    List<EmployeeDocument> findByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);
    java.util.Optional<EmployeeDocument> findByIdAndTenantId(UUID id, UUID tenantId);
}
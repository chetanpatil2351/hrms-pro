package com.hrmspro.modules.shift;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShiftAssignmentHistoryRepository extends JpaRepository<ShiftAssignmentHistory, UUID> {

    List<ShiftAssignmentHistory> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(UUID tenantId, UUID employeeId);

    List<ShiftAssignmentHistory> findByAssignmentIdOrderByCreatedAtAsc(UUID assignmentId);
}

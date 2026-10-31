package com.hrmspro.modules.leave;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeLeaveBalanceRepository extends JpaRepository<EmployeeLeaveBalance, UUID> {

    Optional<EmployeeLeaveBalance> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<EmployeeLeaveBalance> findByTenantIdAndEmployeeIdAndLeaveTypeIdAndCycleYear(
            UUID tenantId, UUID employeeId, UUID leaveTypeId, Integer cycleYear);

    List<EmployeeLeaveBalance> findByTenantIdAndEmployeeIdAndCycleYear(
            UUID tenantId, UUID employeeId, Integer cycleYear);

    List<EmployeeLeaveBalance> findByTenantIdAndCycleYear(UUID tenantId, Integer cycleYear);
}

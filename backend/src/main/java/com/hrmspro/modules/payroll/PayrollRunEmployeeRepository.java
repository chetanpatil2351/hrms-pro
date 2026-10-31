package com.hrmspro.modules.payroll;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayrollRunEmployeeRepository extends JpaRepository<PayrollRunEmployee, UUID> {
    Optional<PayrollRunEmployee> findByIdAndTenantId(UUID id, UUID tenantId);
    List<PayrollRunEmployee> findByPayrollRunId(UUID payrollRunId);
    Optional<PayrollRunEmployee> findByPayrollRunIdAndEmployeeId(UUID payrollRunId, UUID employeeId);
    Page<PayrollRunEmployee> findByPayrollRunId(UUID payrollRunId, Pageable pageable);
}

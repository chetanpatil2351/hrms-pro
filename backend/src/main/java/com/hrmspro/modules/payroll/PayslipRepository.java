package com.hrmspro.modules.payroll;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayslipRepository extends JpaRepository<Payslip, UUID> {
    Optional<Payslip> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<Payslip> findByTenantIdAndPayslipNumber(UUID tenantId, String payslipNumber);
    Optional<Payslip> findByPayrollPeriodIdAndEmployeeId(UUID payrollPeriodId, UUID employeeId);
    List<Payslip> findByTenantIdAndEmployeeIdOrderByIssueDateDesc(UUID tenantId, UUID employeeId);
    Page<Payslip> findByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId, Pageable pageable);
    Page<Payslip> findByTenantIdAndPayrollPeriodId(UUID tenantId, UUID payrollPeriodId, Pageable pageable);
    boolean existsByTenantIdAndPayslipNumber(UUID tenantId, String payslipNumber);
}

package com.hrmspro.modules.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayrollCalculationSnapshotRepository extends JpaRepository<PayrollCalculationSnapshot, UUID> {
    Optional<PayrollCalculationSnapshot> findByPayrollRunEmployeeId(UUID payrollRunEmployeeId);
}

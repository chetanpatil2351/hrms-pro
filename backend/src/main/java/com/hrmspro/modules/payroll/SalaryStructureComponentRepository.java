package com.hrmspro.modules.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SalaryStructureComponentRepository extends JpaRepository<SalaryStructureComponent, UUID> {
    List<SalaryStructureComponent> findBySalaryStructureIdOrderByDisplayOrderAsc(UUID salaryStructureId);
    Optional<SalaryStructureComponent> findBySalaryStructureIdAndSalaryComponentId(UUID salaryStructureId, UUID salaryComponentId);
    void deleteBySalaryStructureId(UUID salaryStructureId);
}

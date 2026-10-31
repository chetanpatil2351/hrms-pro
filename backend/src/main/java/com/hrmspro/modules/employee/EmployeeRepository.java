package com.hrmspro.modules.employee;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Employee> findByTenantIdAndEmployeeCode(UUID tenantId, String employeeCode);

    Optional<Employee> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    Optional<Employee> findByUserIdAndTenantId(UUID userId, UUID tenantId);

    boolean existsByTenantIdAndEmployeeCode(UUID tenantId, String employeeCode);

    long countByTenantIdAndEmploymentStatus(UUID tenantId, String status);

    long countByTenantId(UUID tenantId);

    List<Employee> findByTenantIdAndBranchId(UUID tenantId, UUID branchId);

    Page<Employee> findByTenantId(UUID tenantId, Pageable pageable);

    @Query("""
        SELECT e FROM Employee e
        WHERE e.tenantId = :tenantId
        AND (:branchId IS NULL OR e.branch.id = :branchId)
        AND (:departmentId IS NULL OR e.department.id = :departmentId)
        AND (:employmentStatus IS NULL OR e.employmentStatus = :employmentStatus)
        AND (:search IS NULL OR (
            LOWER(e.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(e.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(e.workEmail) LIKE LOWER(CONCAT('%', :search, '%'))
        ))
    """)
    Page<Employee> findEmployeesWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("branchId") UUID branchId,
            @Param("departmentId") UUID departmentId,
            @Param("employmentStatus") String employmentStatus,
            @Param("search") String search,
            Pageable pageable
    );
}
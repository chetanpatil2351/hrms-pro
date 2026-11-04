package com.hrmspro.modules.employee;

import com.hrmspro.common.exception.ResourceNotFoundException;
import com.hrmspro.modules.employee.dto.*;
import com.hrmspro.modules.rbac.Role;
import com.hrmspro.modules.rbac.RoleRepository;
import com.hrmspro.modules.tenant.*;
import com.hrmspro.modules.tenant.dto.InviteMemberRequest;
import com.hrmspro.modules.user.AppUser;
import com.hrmspro.modules.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeDocumentRepository documentRepository;
    private final EmployeeAuditLogRepository auditLogRepository;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final BranchRepository branchRepository;
    private final DepartmentRepository departmentRepository;
    private final AppUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantService tenantService;

    @Transactional
    public EmployeeResponseDto createEmployee(UUID tenantId, UUID actorUserId, CreateEmployeeRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        // 1. Capacity Check
        long currentCount = employeeRepository.countByTenantId(tenantId);
        if (currentCount >= tenant.getMaxEmployees()) {
            throw new IllegalStateException("Company employee capacity reached (" +
                    currentCount + "/" + tenant.getMaxEmployees() + "). Please upgrade subscription plan.");
        }

        // 2. Validate Branch belongs to Tenant
        Branch branch = null;
        if (request.getBranchId() != null) {
            branch = branchRepository.findById(request.getBranchId())
                    .filter(b -> b.getTenantId().equals(tenantId))
                    .orElseThrow(() -> new IllegalArgumentException("Branch does not belong to this company"));
        }

        // 3. Validate Department belongs to Tenant and matches Branch if department is branch-scoped
        Department department = null;
        if (request.getDepartmentId() != null) {
            department = departmentRepository.findById(request.getDepartmentId())
                    .filter(d -> d.getTenantId().equals(tenantId))
                    .orElseThrow(() -> new IllegalArgumentException("Department does not belong to this company"));

            if (department.getBranch() != null && branch != null && !department.getBranch().getId().equals(branch.getId())) {
                throw new IllegalArgumentException("Selected department belongs to a different branch (" +
                        department.getBranch().getName() + ")");
            }
        }

        // 4. Validate Reporting Manager belongs to same Tenant
        Employee reportingManager = null;
        if (request.getReportingManagerId() != null) {
            reportingManager = employeeRepository.findById(request.getReportingManagerId())
                    .filter(m -> m.getTenantId().equals(tenantId))
                    .orElseThrow(() -> new IllegalArgumentException("Reporting manager does not belong to this company"));
        }

        // 5. Generate or Validate Employee Code
        String employeeCode = request.getEmployeeCode();
        if (employeeCode == null || employeeCode.isBlank()) {
            employeeCode = generateUniqueEmployeeCode(tenantId, tenant.getCompanyCode());
        } else {
            employeeCode = employeeCode.trim().toUpperCase();
            if (employeeRepository.existsByTenantIdAndEmployeeCode(tenantId, employeeCode)) {
                throw new IllegalArgumentException("Employee code '" + employeeCode + "' is already in use in this company");
            }
        }

        // 6. Check existing user account linking
        AppUser linkedUser = null;
        String workEmail = request.getWorkEmail() != null ? request.getWorkEmail().trim().toLowerCase() : null;
        if (workEmail != null) {
            linkedUser = userRepository.findByEmail(workEmail).orElse(null);
        }

        TenantMembership membership = null;
        if (linkedUser != null) {
            membership = membershipRepository.findByUserIdAndTenantId(linkedUser.getId(), tenantId).orElse(null);
        }

        // 7. Create Employee entity
        Employee employee = Employee.builder()
                .user(linkedUser)
                .membership(membership)
                .branch(branch)
                .department(department)
                .reportingManager(reportingManager)
                .employeeCode(employeeCode)
                .firstName(request.getFirstName().trim())
                .middleName(request.getMiddleName() != null ? request.getMiddleName().trim() : null)
                .lastName(request.getLastName().trim())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .personalEmail(request.getPersonalEmail() != null ? request.getPersonalEmail().trim().toLowerCase() : null)
                .workEmail(workEmail)
                .phone(request.getPhone())
                .address(request.getAddress())
                .profilePhotoUrl(request.getProfilePhotoUrl())
                .joiningDate(request.getJoiningDate())
                .employmentType(request.getEmploymentType() != null ? request.getEmploymentType() : "FULL_TIME")
                .designation(request.getDesignation().trim())
                .probationEndDate(request.getProbationEndDate())
                .employmentStatus("ACTIVE")
                .emergencyContactName(request.getEmergencyContactName())
                .emergencyContactRelation(request.getEmergencyContactRelation())
                .emergencyContactPhone(request.getEmergencyContactPhone())
                .panNumberMasked(maskPan(request.getPanNumber()))
                .aadhaarNumberMasked(maskAadhaar(request.getAadhaarNumber()))
                .createdBy(actorUserId)
                .updatedBy(actorUserId)
                .build();
        employee.setTenantId(tenantId);
        employee = employeeRepository.save(employee);

        // 8. Create Audit Log
        recordAudit(tenantId, employee, actorUserId, "CREATED", null, null, null, "Employee record created");

        // 9. Optional Account Invitation
        if (request.isCreateAccountInvitation() && workEmail != null && linkedUser == null) {
            UUID roleId = request.getRoleId();
            if (roleId == null) {
                Role empRole = roleRepository.findByNameAndTenantIdIsNull("EMPLOYEE")
                        .orElseThrow(() -> new ResourceNotFoundException("Default EMPLOYEE role not found"));
                roleId = empRole.getId();
            }
            tenantService.inviteMember(tenantId, actorUserId, InviteMemberRequest.builder()
                    .email(workEmail)
                    .roleId(roleId)
                    .branchId(branch != null ? branch.getId() : null)
                    .departmentId(department != null ? department.getId() : null)
                    .designation(employee.getDesignation())
                    .employeeCode(employee.getEmployeeCode())
                    .build());
        }

        return mapToEmployeeResponse(employee);
    }


    @Transactional(readOnly = true)
    public Page<EmployeeResponseDto> getEmployees(
            UUID tenantId,
            UUID branchId,
            UUID departmentId,
            String employmentStatus,
            String search,
            Pageable pageable
    ) {
        String safeSearch = search == null ? "" : search.trim();

        return employeeRepository.findEmployeesWithFilters(
                tenantId,
                branchId,
                departmentId,
                employmentStatus,
                safeSearch,
                pageable
        ).map(this::mapToEmployeeResponse);
    }


    @Transactional(readOnly = true)
    public EmployeeResponseDto getEmployeeById(UUID tenantId, UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .filter(e -> e.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found in this company"));
        return mapToEmployeeResponse(employee);
    }

    @Transactional(readOnly = true)
    public EmployeeResponseDto getCurrentEmployeeProfile(UUID tenantId, UUID userId) {
        Employee employee = employeeRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("No employee profile found for current user in this company"));
        return mapToEmployeeResponse(employee);
    }

    @Transactional
    public EmployeeResponseDto updateEmployee(UUID tenantId, UUID actorUserId, UUID employeeId, UpdateEmployeeRequest request) {
        Employee employee = employeeRepository.findById(employeeId)
                .filter(e -> e.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found in this company"));

        // Validate Branch
        Branch branch = null;
        if (request.getBranchId() != null) {
            branch = branchRepository.findById(request.getBranchId())
                    .filter(b -> b.getTenantId().equals(tenantId))
                    .orElseThrow(() -> new IllegalArgumentException("Branch does not belong to this company"));
            employee.setBranch(branch);
        } else {
            employee.setBranch(null);
        }

        // Validate Department
        if (request.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(request.getDepartmentId())
                    .filter(d -> d.getTenantId().equals(tenantId))
                    .orElseThrow(() -> new IllegalArgumentException("Department does not belong to this company"));

            if (dept.getBranch() != null && branch != null && !dept.getBranch().getId().equals(branch.getId())) {
                throw new IllegalArgumentException("Selected department belongs to a different branch (" +
                        dept.getBranch().getName() + ")");
            }
            employee.setDepartment(dept);
        } else {
            employee.setDepartment(null);
        }

        // Validate Manager (cannot be self)
        if (request.getReportingManagerId() != null) {
            if (request.getReportingManagerId().equals(employeeId)) {
                throw new IllegalArgumentException("An employee cannot report to themselves");
            }
            Employee mgr = employeeRepository.findById(request.getReportingManagerId())
                    .filter(m -> m.getTenantId().equals(tenantId))
                    .orElseThrow(() -> new IllegalArgumentException("Reporting manager does not belong to this company"));
            employee.setReportingManager(mgr);
        } else {
            employee.setReportingManager(null);
        }

        employee.setFirstName(request.getFirstName().trim());
        employee.setMiddleName(request.getMiddleName() != null ? request.getMiddleName().trim() : null);
        employee.setLastName(request.getLastName().trim());
        employee.setDateOfBirth(request.getDateOfBirth());
        employee.setGender(request.getGender());
        employee.setPersonalEmail(request.getPersonalEmail());
        employee.setWorkEmail(request.getWorkEmail());
        employee.setPhone(request.getPhone());
        employee.setAddress(request.getAddress());
        employee.setProfilePhotoUrl(request.getProfilePhotoUrl());
        if (request.getEmploymentType() != null) employee.setEmploymentType(request.getEmploymentType());
        if (request.getDesignation() != null) employee.setDesignation(request.getDesignation().trim());
        employee.setProbationEndDate(request.getProbationEndDate());
        employee.setEmergencyContactName(request.getEmergencyContactName());
        employee.setEmergencyContactRelation(request.getEmergencyContactRelation());
        employee.setEmergencyContactPhone(request.getEmergencyContactPhone());
        employee.setUpdatedBy(actorUserId);

        employee = employeeRepository.save(employee);
        recordAudit(tenantId, employee, actorUserId, "UPDATED", null, null, null, "Employee profile updated");

        return mapToEmployeeResponse(employee);
    }

    @Transactional(readOnly = true)
    public EmployeeStatsDto getEmployeeStats(UUID tenantId) {
        long totalEmployees = employeeRepository.countByTenantId(tenantId);
        long activeEmployees = employeeRepository.countByTenantIdAndEmploymentStatusIgnoreCase(tenantId, "ACTIVE");
        long inactiveEmployees = employeeRepository.countByTenantIdAndEmploymentStatusIgnoreCase(tenantId, "INACTIVE");
        long probationEmployees = employeeRepository.countByTenantIdAndEmploymentStatusIgnoreCase(tenantId, "PROBATION");
        long terminatedEmployees = employeeRepository.countByTenantIdAndEmploymentStatusIgnoreCase(tenantId, "TERMINATED");
        long totalDepartments = departmentRepository.countByTenantId(tenantId);
        long totalBranches = branchRepository.countByTenantId(tenantId);

        return EmployeeStatsDto.builder()
                .totalEmployees(totalEmployees)
                .activeEmployees(activeEmployees)
                .inactiveEmployees(inactiveEmployees)
                .probationEmployees(probationEmployees)
                .terminatedEmployees(terminatedEmployees)
                .totalDepartments(totalDepartments)
                .totalBranches(totalBranches)
                .build();
    }

    @Transactional
    public EmployeeResponseDto updateEmployeeStatus(UUID tenantId, UUID actorUserId, UUID employeeId, String newStatus) {
        Employee employee = employeeRepository.findById(employeeId)
                .filter(e -> e.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found in this company"));

        String oldStatus = employee.getEmploymentStatus();
        employee.setEmploymentStatus(newStatus.toUpperCase());
        employee.setUpdatedBy(actorUserId);
        employee = employeeRepository.save(employee);

        recordAudit(tenantId, employee, actorUserId, "STATUS_CHANGED", "employmentStatus", oldStatus, newStatus, "Status updated to " + newStatus);

        return mapToEmployeeResponse(employee);
    }

    // --- Document Management ---

    @Transactional
    public EmployeeDocumentDto addEmployeeDocument(UUID tenantId, UUID actorUserId, UUID employeeId, EmployeeDocumentDto request) {
        Employee employee = employeeRepository.findById(employeeId)
                .filter(e -> e.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found in this company"));

        EmployeeDocument document = EmployeeDocument.builder()
                .employee(employee)
                .documentType(request.getDocumentType().toUpperCase())
                .fileName(request.getFileName().trim())
                .filePath(request.getFilePath().trim())
                .mimeType(request.getMimeType().trim())
                .fileSizeBytes(request.getFileSizeBytes())
                .uploadedBy(actorUserId)
                .build();
        document.setTenantId(tenantId);
        document = documentRepository.save(document);

        recordAudit(tenantId, employee, actorUserId, "DOCUMENT_UPLOADED", "document", null, document.getFileName(), "Document uploaded: " + document.getDocumentType());

        return mapToDocumentDto(document);
    }

    @Transactional(readOnly = true)
    public List<EmployeeDocumentDto> getEmployeeDocuments(UUID tenantId, UUID employeeId) {
        return documentRepository.findByTenantIdAndEmployeeId(tenantId, employeeId).stream()
                .map(this::mapToDocumentDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EmployeeDocument getEmployeeDocumentEntity(UUID tenantId, UUID employeeId, UUID documentId) {
        return documentRepository.findByIdAndTenantId(documentId, tenantId)
                .filter(doc -> doc.getEmployee().getId().equals(employeeId))
                .orElseThrow(() -> new ResourceNotFoundException("Document not found for this employee"));
    }

    @Transactional(readOnly = true)
    public List<EmployeeAuditLogDto> getEmployeeHistory(UUID tenantId, UUID employeeId) {
        return auditLogRepository.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(tenantId, employeeId).stream()
                .map(this::mapToAuditLogDto)
                .collect(Collectors.toList());
    }

    // --- Helpers ---

    private synchronized String generateUniqueEmployeeCode(UUID tenantId, String companyCode) {
        long count = employeeRepository.countByTenantId(tenantId) + 1;
        String prefix = companyCode != null ? companyCode : "EMP";
        String candidate = String.format("%s-%04d", prefix, count);

        int attempts = 0;
        while (employeeRepository.existsByTenantIdAndEmployeeCode(tenantId, candidate)) {
            count++;
            candidate = String.format("%s-%04d", prefix, count);
            attempts++;
            if (attempts > 1000) {
                candidate = String.format("%s-%s", prefix, UUID.randomUUID().toString().substring(0, 6).toUpperCase());
                break;
            }
        }
        return candidate;
    }

    private void recordAudit(UUID tenantId, Employee employee, UUID actorId, String action, String fieldName, String oldVal, String newVal, String details) {
        EmployeeAuditLog logEntry = EmployeeAuditLog.builder()
                .employee(employee)
                .actorId(actorId)
                .action(action)
                .fieldName(fieldName)
                .oldValue(oldVal)
                .newValue(newVal)
                .details(details)
                .build();
        logEntry.setTenantId(tenantId);
        auditLogRepository.save(logEntry);
    }

    private String maskPan(String pan) {
        if (pan == null || pan.length() < 10) return null;
        // Standard PAN mask: first 2 chars + 6 X's + last 2 chars (e.g. ABXXXXXX4F)
        return pan.substring(0, 2) + "XXXXXX" + pan.substring(8);
    }

    private String maskAadhaar(String aadhaar) {
        if (aadhaar == null || aadhaar.length() < 12) return null;
        return "XXXX-XXXX-" + aadhaar.substring(8);
    }

    private EmployeeResponseDto mapToEmployeeResponse(Employee e) {
        String fullName = (e.getFirstName() + " " + (e.getMiddleName() != null ? e.getMiddleName() + " " : "") + e.getLastName()).trim();
        return EmployeeResponseDto.builder()
                .id(e.getId())
                .tenantId(e.getTenantId())
                .userId(e.getUser() != null ? e.getUser().getId() : null)
                .membershipId(e.getMembership() != null ? e.getMembership().getId() : null)
                .employeeCode(e.getEmployeeCode())
                .firstName(e.getFirstName())
                .middleName(e.getMiddleName())
                .lastName(e.getLastName())
                .fullName(fullName)
                .dateOfBirth(e.getDateOfBirth())
                .gender(e.getGender())
                .personalEmail(e.getPersonalEmail())
                .workEmail(e.getWorkEmail())
                .phone(e.getPhone())
                .address(e.getAddress())
                .profilePhotoUrl(e.getProfilePhotoUrl())
                .joiningDate(e.getJoiningDate())
                .employmentType(e.getEmploymentType())
                .designation(e.getDesignation())
                .employmentStatus(e.getEmploymentStatus())
                .probationEndDate(e.getProbationEndDate())
                .branchId(e.getBranch() != null ? e.getBranch().getId() : null)
                .branchName(e.getBranch() != null ? e.getBranch().getName() : null)
                .departmentId(e.getDepartment() != null ? e.getDepartment().getId() : null)
                .departmentName(e.getDepartment() != null ? e.getDepartment().getName() : null)
                .reportingManagerId(e.getReportingManager() != null ? e.getReportingManager().getId() : null)
                .reportingManagerName(e.getReportingManager() != null ?
                        (e.getReportingManager().getFirstName() + " " + e.getReportingManager().getLastName()).trim() : null)
                .emergencyContactName(e.getEmergencyContactName())
                .emergencyContactRelation(e.getEmergencyContactRelation())
                .emergencyContactPhone(e.getEmergencyContactPhone())
                .panNumberMasked(e.getPanNumberMasked())
                .aadhaarNumberMasked(e.getAadhaarNumberMasked())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private EmployeeDocumentDto mapToDocumentDto(EmployeeDocument doc) {
        return EmployeeDocumentDto.builder()
                .id(doc.getId())
                .employeeId(doc.getEmployee().getId())
                .documentType(doc.getDocumentType())
                .fileName(doc.getFileName())
                .filePath(doc.getFilePath())
                .mimeType(doc.getMimeType())
                .fileSizeBytes(doc.getFileSizeBytes())
                .createdAt(doc.getCreatedAt())
                .build();
    }

    private EmployeeAuditLogDto mapToAuditLogDto(EmployeeAuditLog log) {
        return EmployeeAuditLogDto.builder()
                .id(log.getId())
                .employeeId(log.getEmployee().getId())
                .actorId(log.getActorId())
                .action(log.getAction())
                .fieldName(log.getFieldName())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .details(log.getDetails())
                .createdAt(log.getCreatedAt())
                .build();
    }
}

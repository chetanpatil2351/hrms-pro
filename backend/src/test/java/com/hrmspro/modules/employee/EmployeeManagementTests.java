package com.hrmspro.modules.employee;

import com.hrmspro.modules.auth.dto.RegisterRequest;
import com.hrmspro.modules.auth.dto.UserProfileDto;
import com.hrmspro.modules.auth.service.AuthService;
import com.hrmspro.modules.employee.dto.*;
import com.hrmspro.modules.rbac.Role;
import com.hrmspro.modules.rbac.RoleRepository;
import com.hrmspro.modules.tenant.*;
import com.hrmspro.modules.tenant.dto.BranchDto;
import com.hrmspro.modules.tenant.dto.CreateTenantRequest;
import com.hrmspro.modules.tenant.dto.DepartmentDto;
import com.hrmspro.modules.tenant.dto.TenantResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmployeeManagementTests {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private AuthService authService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmployeeDocumentRepository documentRepository;

    @Autowired
    private EmployeeAuditLogRepository auditLogRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private RoleRepository roleRepository;

    private UserProfileDto hrUser;
    private TenantResponseDto tenantA;
    private TenantResponseDto tenantB;
    private BranchDto branchA;
    private DepartmentDto deptA;

    @BeforeEach
    void setUp() {
        hrUser = authService.register(RegisterRequest.builder()
                .email("hr.manager@test.com")
                .password("SecurePass123!")
                .firstName("Sarah")
                .lastName("Connor")
                .build());

        tenantA = tenantService.createTenant(hrUser.getId(), CreateTenantRequest.builder()
                .name("Skynet Systems")
                .subdomain("skynet")
                .companyCode("SKY")
                .email("admin@skynet.com")
                .build());

        tenantB = tenantService.createTenant(hrUser.getId(), CreateTenantRequest.builder()
                .name("Resistance Corp")
                .subdomain("resistance")
                .companyCode("RES")
                .email("admin@resistance.com")
                .build());

        branchA = tenantService.createBranch(tenantA.getId(), BranchDto.builder()
                .name("Silicon Valley Branch")
                .code("SV-01")
                .build());

        deptA = tenantService.createDepartment(tenantA.getId(), DepartmentDto.builder()
                .name("Robotics")
                .code("ROB")
                .branchId(branchA.getId())
                .build());
    }

    @Test
    @DisplayName("Employee creation generates unique code, masks PAN and Aadhaar, and records audit log")
    void testCreateEmployee() {
        CreateEmployeeRequest request = CreateEmployeeRequest.builder()
                .firstName("John")
                .middleName("R")
                .lastName("Connor")
                .personalEmail("john.connor@gmail.com")
                .workEmail("john@skynet.com")
                .phone("9876543210")
                .joiningDate(LocalDate.of(2025, 1, 15))
                .employmentType("FULL_TIME")
                .designation("Lead Architect")
                .branchId(branchA.getId())
                .departmentId(deptA.getId())
                .panNumber("ABCDE1234F")
                .aadhaarNumber("123456789012")
                .build();

        EmployeeResponseDto response = employeeService.createEmployee(tenantA.getId(), hrUser.getId(), request);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getEmployeeCode()).startsWith("SKY-");
        assertThat(response.getFullName()).isEqualTo("John R Connor");
        assertThat(response.getPanNumberMasked()).isEqualTo("ABXXXXXX4F");
        assertThat(response.getAadhaarNumberMasked()).isEqualTo("XXXX-XXXX-9012");
        assertThat(response.getBranchName()).isEqualTo("Silicon Valley Branch");
        assertThat(response.getDepartmentName()).isEqualTo("Robotics");

        // Audit log check
        List<EmployeeAuditLogDto> history = employeeService.getEmployeeHistory(tenantA.getId(), response.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getAction()).isEqualTo("CREATED");
    }

    @Test
    @DisplayName("Cross-tenant branch and department assignments are strictly rejected")
    void testCrossTenantRejections() {
        BranchDto branchB = tenantService.createBranch(tenantB.getId(), BranchDto.builder()
                .name("Resistance Base")
                .code("RES-01")
                .build());

        // Attempt to create employee in Tenant A with branch belonging to Tenant B
        CreateEmployeeRequest request = CreateEmployeeRequest.builder()
                .firstName("Kyle")
                .lastName("Reese")
                .joiningDate(LocalDate.now())
                .designation("Scout")
                .branchId(branchB.getId()) // cross tenant!
                .build();

        assertThrows(IllegalArgumentException.class, () ->
                employeeService.createEmployee(tenantA.getId(), hrUser.getId(), request)
        );
    }

    @Test
    @DisplayName("Employee cannot report to themselves")
    void testSelfReportingManagerRejection() {
        CreateEmployeeRequest request = CreateEmployeeRequest.builder()
                .firstName("Marcus")
                .lastName("Wright")
                .joiningDate(LocalDate.now())
                .designation("Cyborg")
                .build();

        EmployeeResponseDto emp = employeeService.createEmployee(tenantA.getId(), hrUser.getId(), request);

        UpdateEmployeeRequest updateReq = UpdateEmployeeRequest.builder()
                .firstName("Marcus")
                .lastName("Wright")
                .reportingManagerId(emp.getId()) // self!
                .build();

        assertThrows(IllegalArgumentException.class, () ->
                employeeService.updateEmployee(tenantA.getId(), hrUser.getId(), emp.getId(), updateReq)
        );
    }

    @Test
    @DisplayName("Employee pagination, status filtering, and search works cleanly")
    void testEmployeeSearchAndFiltering() {
        for (int i = 1; i <= 5; i++) {
            employeeService.createEmployee(tenantA.getId(), hrUser.getId(), CreateEmployeeRequest.builder()
                    .firstName("Worker" + i)
                    .lastName("Test")
                    .workEmail("worker" + i + "@skynet.com")
                    .joiningDate(LocalDate.now())
                    .designation("Operator")
                    .branchId(branchA.getId())
                    .build());
        }

        Page<EmployeeResponseDto> searchResult = employeeService.getEmployees(
                tenantA.getId(),
                branchA.getId(),
                null,
                "ACTIVE",
                "Worker3",
                PageRequest.of(0, 10)
        );

        assertThat(searchResult.getTotalElements()).isEqualTo(1);
        assertThat(searchResult.getContent().get(0).getFirstName()).isEqualTo("Worker3");
    }

    @Test
    @DisplayName("Employee document metadata upload and retrieval")
    void testEmployeeDocumentManagement() {
        EmployeeResponseDto emp = employeeService.createEmployee(tenantA.getId(), hrUser.getId(), CreateEmployeeRequest.builder()
                .firstName("Miles")
                .lastName("Dyson")
                .joiningDate(LocalDate.now())
                .designation("Director of Research")
                .build());

        EmployeeDocumentDto docDto = EmployeeDocumentDto.builder()
                .documentType("RESUME")
                .fileName("miles_resume.pdf")
                .filePath("tenants/" + tenantA.getId() + "/employees/" + emp.getId() + "/resume.pdf")
                .mimeType("application/pdf")
                .fileSizeBytes(102400L)
                .build();

        EmployeeDocumentDto createdDoc = employeeService.addEmployeeDocument(tenantA.getId(), hrUser.getId(), emp.getId(), docDto);
        assertThat(createdDoc.getId()).isNotNull();

        List<EmployeeDocumentDto> docs = employeeService.getEmployeeDocuments(tenantA.getId(), emp.getId());
        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getFileName()).isEqualTo("miles_resume.pdf");

        // Verify audit log recorded DOCUMENT_UPLOADED
        List<EmployeeAuditLogDto> history = employeeService.getEmployeeHistory(tenantA.getId(), emp.getId());
        assertThat(history).extracting(EmployeeAuditLogDto::getAction).contains("DOCUMENT_UPLOADED");
    }

    @Test
    @DisplayName("Employee dashboard statistics return accurate tenant-wide counts")
    void testEmployeeDashboardStatistics() {
        EmployeeStatsDto initialStats = employeeService.getEmployeeStats(tenantA.getId());
        long baseTotal = initialStats.getTotalEmployees();
        long baseActive = initialStats.getActiveEmployees();

        EmployeeResponseDto emp1 = employeeService.createEmployee(tenantA.getId(), hrUser.getId(), CreateEmployeeRequest.builder()
                .firstName("Sarah")
                .lastName("Connor")
                .joiningDate(LocalDate.now())
                .designation("Sergeant")
                .build());

        EmployeeStatsDto afterCreate = employeeService.getEmployeeStats(tenantA.getId());
        assertThat(afterCreate.getTotalEmployees()).isEqualTo(baseTotal + 1);
        assertThat(afterCreate.getActiveEmployees()).isEqualTo(baseActive + 1);

        // Deactivate employee
        employeeService.updateEmployeeStatus(tenantA.getId(), hrUser.getId(), emp1.getId(), "INACTIVE");
        EmployeeStatsDto afterDeactivate = employeeService.getEmployeeStats(tenantA.getId());
        assertThat(afterDeactivate.getActiveEmployees()).isEqualTo(baseActive);
        assertThat(afterDeactivate.getInactiveEmployees()).isEqualTo(initialStats.getInactiveEmployees() + 1);

        // Reactivate employee
        employeeService.updateEmployeeStatus(tenantA.getId(), hrUser.getId(), emp1.getId(), "ACTIVE");
        EmployeeStatsDto afterReactivate = employeeService.getEmployeeStats(tenantA.getId());
        assertThat(afterReactivate.getActiveEmployees()).isEqualTo(baseActive + 1);
    }

    @Test
    @DisplayName("Branch-scoped department validation rejects mismatching department")
    void testDepartmentBranchMismatchValidation() {
        BranchDto branch2 = tenantService.createBranch(tenantA.getId(), BranchDto.builder()
                .name("Texas Office")
                .code("TX-01")
                .build());

        // deptA belongs to branchA (Silicon Valley). Trying to assign to branch2 (Texas) should fail.
        CreateEmployeeRequest invalidReq = CreateEmployeeRequest.builder()
                .firstName("Miles")
                .lastName("Bennett")
                .joiningDate(LocalDate.now())
                .designation("Specialist")
                .branchId(branch2.getId())
                .departmentId(deptA.getId()) // belongs to branchA
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                employeeService.createEmployee(tenantA.getId(), hrUser.getId(), invalidReq)
        );
        assertThat(ex.getMessage()).contains("Selected department belongs to a different branch");
    }
}
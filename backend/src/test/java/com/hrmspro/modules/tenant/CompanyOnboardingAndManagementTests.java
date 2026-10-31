package com.hrmspro.modules.tenant;

import com.hrmspro.common.exception.ResourceNotFoundException;
import com.hrmspro.modules.auth.dto.RegisterRequest;
import com.hrmspro.modules.auth.dto.UserProfileDto;
import com.hrmspro.modules.auth.service.AuthService;
import com.hrmspro.modules.rbac.Role;
import com.hrmspro.modules.rbac.RoleRepository;
import com.hrmspro.modules.tenant.dto.*;
import com.hrmspro.modules.user.AppUser;
import com.hrmspro.modules.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CompanyOnboardingAndManagementTests {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private AuthService authService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantMembershipRepository membershipRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private TenantInvitationRepository invitationRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private UserProfileDto creatorUser;
    private UserProfileDto inviteeUser;
    private Role employeeRole;

    @BeforeEach
    void setUp() {
        creatorUser = authService.register(RegisterRequest.builder()
                .email("creator@enterprise.com")
                .password("Password123!")
                .firstName("Boss")
                .lastName("Man")
                .build());

        inviteeUser = authService.register(RegisterRequest.builder()
                .email("invitee@enterprise.com")
                .password("Password123!")
                .firstName("Staff")
                .lastName("Member")
                .build());

        employeeRole = roleRepository.save(Role.builder()
                .name("EMPLOYEE")
                .isSystemRole(true)
                .build());
    }

    @Test
    @DisplayName("Company onboarding creates tenant, default head office branch, and assigns COMPANY_ADMIN to creator")
    void testCompanyOnboarding() {
        CreateTenantRequest request = CreateTenantRequest.builder()
                .name("Stark Industries")
                .subdomain("stark")
                .companyCode("STRK")
                .email("contact@stark.com")
                .city("New York")
                .state("NY")
                .industry("Defense")
                .build();

        TenantResponseDto tenant = tenantService.createTenant(creatorUser.getId(), request);

        assertThat(tenant.getId()).isNotNull();
        assertThat(tenant.getName()).isEqualTo("Stark Industries");
        assertThat(tenant.getMaxEmployees()).isEqualTo(10);
        assertThat(tenant.getCurrentEmployeeCount()).isEqualTo(1L);

        // Verify membership & role
        List<TenantMembership> memberships = membershipRepository.findByUserId(creatorUser.getId());
        assertThat(memberships).hasSize(1);
        TenantMembership m = memberships.get(0);
        assertThat(m.getTenantId()).isEqualTo(tenant.getId());
        assertThat(m.getStatus()).isEqualTo("ACTIVE");
        assertThat(m.getRoles()).extracting(Role::getName).contains("COMPANY_ADMIN");

        // Verify default branch
        List<Branch> branches = branchRepository.findByTenantId(tenant.getId());
        assertThat(branches).hasSize(1);
        assertThat(branches.get(0).getName()).isEqualTo("Head Office");
        assertThat(branches.get(0).getCode()).isEqualTo("HO");
    }

    @Test
    @DisplayName("Duplicate subdomain or company code rejection")
    void testDuplicateSubdomainOrCode() {
        CreateTenantRequest r1 = CreateTenantRequest.builder()
                .name("Wayne Enterprises")
                .subdomain("wayne")
                .companyCode("WAYNE")
                .email("bruce@wayne.com")
                .build();
        tenantService.createTenant(creatorUser.getId(), r1);

        // Same subdomain
        CreateTenantRequest r2 = CreateTenantRequest.builder()
                .name("Wayne Foundation")
                .subdomain("wayne")
                .companyCode("WFD")
                .email("info@wayne.com")
                .build();
        assertThrows(IllegalArgumentException.class, () -> tenantService.createTenant(creatorUser.getId(), r2));

        // Same code
        CreateTenantRequest r3 = CreateTenantRequest.builder()
                .name("Wayne Capital")
                .subdomain("waynecap")
                .companyCode("WAYNE")
                .email("info2@wayne.com")
                .build();
        assertThrows(IllegalArgumentException.class, () -> tenantService.createTenant(creatorUser.getId(), r3));
    }

    @Test
    @DisplayName("Branch creation, listing, updating, and duplicate code rejection")
    void testBranchManagement() {
        TenantResponseDto tenant = tenantService.createTenant(creatorUser.getId(), CreateTenantRequest.builder()
                .name("Acme Corp")
                .subdomain("acme-corp")
                .companyCode("ACM")
                .email("hr@acme.com")
                .build());

        BranchDto b1 = tenantService.createBranch(tenant.getId(), BranchDto.builder()
                .name("Pune Tech Park")
                .code("PUN-01")
                .city("Pune")
                .build());

        assertThat(b1.getId()).isNotNull();

        // Duplicate code in same tenant must fail
        assertThrows(IllegalArgumentException.class, () -> tenantService.createBranch(tenant.getId(), BranchDto.builder()
                .name("Pune Operations")
                .code("PUN-01")
                .build()));

        // Update branch
        b1.setName("Pune Innovation Center");
        BranchDto updated = tenantService.updateBranch(tenant.getId(), b1.getId(), b1);
        assertThat(updated.getName()).isEqualTo("Pune Innovation Center");

        // List branches
        List<BranchDto> branches = tenantService.getBranches(tenant.getId());
        assertThat(branches).hasSize(2); // Head Office + Pune
    }

    @Test
    @DisplayName("Department creation and cross-tenant branch rejection")
    void testDepartmentManagement() {
        TenantResponseDto tenantA = tenantService.createTenant(creatorUser.getId(), CreateTenantRequest.builder()
                .name("Company A")
                .subdomain("comp-a")
                .companyCode("CPA")
                .email("a@a.com")
                .build());

        TenantResponseDto tenantB = tenantService.createTenant(creatorUser.getId(), CreateTenantRequest.builder()
                .name("Company B")
                .subdomain("comp-b")
                .companyCode("CPB")
                .email("b@b.com")
                .build());

        Branch branchOfB = branchRepository.findByTenantId(tenantB.getId()).get(0);

        // Attempt to associate Tenant A department with Tenant B branch must fail
        assertThrows(IllegalArgumentException.class, () -> {
            tenantService.createDepartment(tenantA.getId(), DepartmentDto.builder()
                    .name("Engineering")
                    .code("ENG")
                    .branchId(branchOfB.getId())
                    .build());
        });

        // Valid department creation in Tenant A
        Branch branchOfA = branchRepository.findByTenantId(tenantA.getId()).get(0);
        DepartmentDto dept = tenantService.createDepartment(tenantA.getId(), DepartmentDto.builder()
                .name("Engineering")
                .code("ENG")
                .branchId(branchOfA.getId())
                .build());

        assertThat(dept.getId()).isNotNull();
        assertThat(dept.getName()).isEqualTo("Engineering");
    }

    @Test
    @DisplayName("Membership invitation, capacity enforcement, and acceptance flow")
    void testInvitationAndAcceptanceFlow() {
        TenantResponseDto tenant = tenantService.createTenant(creatorUser.getId(), CreateTenantRequest.builder()
                .name("Cyberdyne")
                .subdomain("cyberdyne")
                .companyCode("CYB")
                .email("t800@cyberdyne.com")
                .build());

        // 1. Invite user
        InvitationResponseDto invite = tenantService.inviteMember(tenant.getId(), creatorUser.getId(), InviteMemberRequest.builder()
                .email(inviteeUser.getEmail())
                .roleId(employeeRole.getId())
                .designation("Junior Engineer")
                .build());

        assertThat(invite.getId()).isNotNull();
        assertThat(invite.getRawToken()).isNotNull();
        assertThat(invite.getStatus()).isEqualTo("PENDING");

        // 2. Accept invitation using the issued raw token
        tenantService.acceptInvitation(inviteeUser.getId(), AcceptInvitationRequest.builder()
                .token(invite.getRawToken())
                .build());

        // Verify membership created
        List<TenantMembership> memberships = membershipRepository.findByUserId(inviteeUser.getId());
        assertThat(memberships).hasSize(1);
        assertThat(memberships.get(0).getTenantId()).isEqualTo(tenant.getId());
        assertThat(memberships.get(0).getStatus()).isEqualTo("ACTIVE");

        // 3. Replay acceptance must fail
        assertThrows(IllegalStateException.class, () -> {
            tenantService.acceptInvitation(inviteeUser.getId(), AcceptInvitationRequest.builder()
                    .token(invite.getRawToken())
                    .build());
        });
    }

    @Test
    @DisplayName("Subscription capacity enforcement rejects invitations when limit reached")
    void testCapacityEnforcement() {
        TenantResponseDto tenant = tenantService.createTenant(creatorUser.getId(), CreateTenantRequest.builder()
                .name("Small Startup")
                .subdomain("small-start")
                .companyCode("SST")
                .email("info@small.com")
                .build());

        // Set max employees to 1
        Tenant t = tenantRepository.findById(tenant.getId()).orElseThrow();
        t.setMaxEmployees(1); // creator is already 1 active member
        tenantRepository.save(t);

        // Attempt to invite another employee must fail with capacity exceeded
        assertThrows(IllegalStateException.class, () -> {
            tenantService.inviteMember(tenant.getId(), creatorUser.getId(), InviteMemberRequest.builder()
                    .email("exceed@enterprise.com")
                    .roleId(employeeRole.getId())
                    .build());
        });
    }
}
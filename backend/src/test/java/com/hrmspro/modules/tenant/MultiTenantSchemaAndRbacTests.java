package com.hrmspro.modules.tenant;

import com.hrmspro.modules.rbac.Role;
import com.hrmspro.modules.rbac.RoleRepository;
import com.hrmspro.modules.user.AppUser;
import com.hrmspro.modules.user.AppUserRepository;
import com.hrmspro.modules.user.RefreshToken;
import com.hrmspro.modules.user.RefreshTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ActiveProfiles("test")
class MultiTenantSchemaAndRbacTests {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private TenantMembershipRepository membershipRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("Single global user can belong to multiple tenants with distinct roles and branches")
    void testSingleUserMultipleTenantsWithDifferentRoles() {
        // 1. Create one global user
        AppUser user = appUserRepository.save(AppUser.builder()
                .email("chetan@example.com")
                .firstName("Chetan")
                .lastName("Patil")
                .passwordHash("argon2-hashed-value")
                .isActive(true)
                .build());

        // 2. Create Tenant A & Tenant B
        Tenant tenantA = tenantRepository.save(Tenant.builder()
                .name("Acme Corp")
                .subdomain("acme")
                .companyCode("ACM")
                .email("hr@acme.com")
                .build());

        Tenant tenantB = tenantRepository.save(Tenant.builder()
                .name("Globex Inc")
                .subdomain("globex")
                .companyCode("GLB")
                .email("hr@globex.com")
                .build());

        // 3. Create Branches with tenantId explicitly set before save
        Branch branchA = Branch.builder()
                .name("Acme Bangalore")
                .code("ACM-BLR")
                .build();
        branchA.setTenantId(tenantA.getId());
        branchA = branchRepository.save(branchA);

        Branch branchB = Branch.builder()
                .name("Globex Mumbai")
                .code("GLB-BOM")
                .build();
        branchB.setTenantId(tenantB.getId());
        branchB = branchRepository.save(branchB);

        // 4. Create Roles
        Role companyAdminRole = roleRepository.save(Role.builder()
                .name("COMPANY_ADMIN")
                .isSystemRole(true)
                .build());

        Role employeeRole = roleRepository.save(Role.builder()
                .name("EMPLOYEE")
                .isSystemRole(true)
                .build());

        // 5. Membership in Tenant A: COMPANY_ADMIN
        TenantMembership membershipA = TenantMembership.builder()
                .user(user)
                .branch(branchA)
                .status("ACTIVE")
                .designation("Director")
                .roles(Set.of(companyAdminRole))
                .build();
        membershipA.setTenantId(tenantA.getId());
        membershipRepository.save(membershipA);

        // 6. Membership in Tenant B: EMPLOYEE
        TenantMembership membershipB = TenantMembership.builder()
                .user(user)
                .branch(branchB)
                .status("ACTIVE")
                .designation("Consultant")
                .roles(Set.of(employeeRole))
                .build();
        membershipB.setTenantId(tenantB.getId());
        membershipRepository.save(membershipB);

        // Assertions
        List<TenantMembership> userMemberships = membershipRepository.findByUserId(user.getId());
        assertThat(userMemberships).hasSize(2);

        Optional<TenantMembership> foundA = membershipRepository.findByUserIdAndTenantId(user.getId(), tenantA.getId());
        assertThat(foundA).isPresent();
        assertThat(foundA.get().getRoles()).extracting(Role::getName).containsExactly("COMPANY_ADMIN");

        Optional<TenantMembership> foundB = membershipRepository.findByUserIdAndTenantId(user.getId(), tenantB.getId());
        assertThat(foundB).isPresent();
        assertThat(foundB.get().getRoles()).extracting(Role::getName).containsExactly("EMPLOYEE");
    }

    @Test
    @DisplayName("Duplicate membership for same user and tenant violates unique constraint")
    void testDuplicateMembershipConstraint() {
        AppUser user = appUserRepository.save(AppUser.builder()
                .email("duplicate@example.com")
                .firstName("John")
                .lastName("Doe")
                .passwordHash("hashed")
                .build());

        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("Apex Systems")
                .subdomain("apex")
                .companyCode("APX")
                .email("hr@apex.com")
                .build());

        TenantMembership m1 = TenantMembership.builder()
                .user(user)
                .status("ACTIVE")
                .build();
        m1.setTenantId(tenant.getId());
        entityManager.persistAndFlush(m1);

        TenantMembership m2 = TenantMembership.builder()
                .user(user)
                .status("INVITED")
                .build();
        m2.setTenantId(tenant.getId());

        assertThrows(Exception.class, () -> {
            entityManager.persistAndFlush(m2);
        });
    }

    @Test
    @DisplayName("Refresh tokens store token family, hash and rotation metadata")
    void testRefreshTokenRotationFoundation() {
        AppUser user = appUserRepository.save(AppUser.builder()
                .email("token.user@example.com")
                .firstName("Alice")
                .lastName("Smith")
                .passwordHash("hashed")
                .build());

        UUID familyId = UUID.randomUUID();
        String initialTokenHash = "sha256_hash_1";

        RefreshToken token = refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .familyId(familyId)
                .tokenHash(initialTokenHash)
                .expiresAt(Instant.now().plusSeconds(604800))
                .revoked(false)
                .build());

        assertThat(token.getId()).isNotNull();
        assertThat(token.getFamilyId()).isEqualTo(familyId);

        // Simulate rotation: mark original as revoked and create successor in same family
        token.setRevoked(true);
        token.setReplacedByHash("sha256_hash_2");
        refreshTokenRepository.saveAndFlush(token);

        RefreshToken rotatedToken = refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .familyId(familyId)
                .tokenHash("sha256_hash_2")
                .expiresAt(Instant.now().plusSeconds(604800))
                .revoked(false)
                .build());

        List<RefreshToken> tokensInFamily = refreshTokenRepository.findByFamilyId(familyId);
        assertThat(tokensInFamily).hasSize(2);
    }
}
package com.hrmspro.modules.auth;

import com.hrmspro.config.security.JwtService;
import com.hrmspro.modules.auth.dto.*;
import com.hrmspro.modules.auth.service.AuthService;
import com.hrmspro.modules.rbac.Role;
import com.hrmspro.modules.rbac.RoleRepository;
import com.hrmspro.modules.tenant.Tenant;
import com.hrmspro.modules.tenant.TenantMembership;
import com.hrmspro.modules.tenant.TenantMembershipRepository;
import com.hrmspro.modules.tenant.TenantRepository;
import com.hrmspro.modules.user.AppUser;
import com.hrmspro.modules.user.AppUserRepository;
import com.hrmspro.modules.user.RefreshToken;
import com.hrmspro.modules.user.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthenticationAndTenantContextTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantMembershipRepository membershipRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtService jwtService;

    private Tenant tenant1;
    private Tenant tenant2;
    private Role adminRole;
    private Role employeeRole;

    @BeforeEach
    void setUp() {
        tenant1 = tenantRepository.save(Tenant.builder()
                .name("Company Alpha")
                .subdomain("alpha")
                .companyCode("ALP")
                .email("admin@alpha.com")
                .build());

        tenant2 = tenantRepository.save(Tenant.builder()
                .name("Company Beta")
                .subdomain("beta")
                .companyCode("BET")
                .email("admin@beta.com")
                .build());

        adminRole = roleRepository.save(Role.builder()
                .name("COMPANY_ADMIN")
                .isSystemRole(true)
                .build());

        employeeRole = roleRepository.save(Role.builder()
                .name("EMPLOYEE")
                .isSystemRole(true)
                .build());
    }

    @Test
    @DisplayName("User registration succeeds and rejects duplicate email")
    void testRegistrationAndDuplicateRejection() {
        RegisterRequest req = RegisterRequest.builder()
                .email("new.user@example.com")
                .password("SecurePass123!")
                .firstName("Alice")
                .lastName("Wonderland")
                .phone("9999999999")
                .build();

        UserProfileDto registered = authService.register(req);
        assertThat(registered.getId()).isNotNull();
        assertThat(registered.getEmail()).isEqualTo("new.user@example.com");

        // Duplicate registration must fail
        assertThrows(IllegalArgumentException.class, () -> authService.register(req));
    }

    @Test
    @DisplayName("User login succeeds and issues valid JWT and Refresh Token")
    void testLoginSuccess() {
        authService.register(RegisterRequest.builder()
                .email("login.test@example.com")
                .password("Password123!")
                .firstName("Bob")
                .lastName("Marley")
                .build());

        LoginRequest loginReq = LoginRequest.builder()
                .email("login.test@example.com")
                .password("Password123!")
                .build();

        AuthResponse res = authService.login(loginReq);
        assertThat(res.getAccessToken()).isNotNull();
        assertThat(res.getRefreshToken()).isNotNull();
        assertThat(jwtService.validateToken(res.getAccessToken())).isTrue();
    }

    @Test
    @DisplayName("Invalid password rejection")
    void testInvalidPasswordRejection() {
        authService.register(RegisterRequest.builder()
                .email("wrong.pass@example.com")
                .password("CorrectPassword123")
                .firstName("Test")
                .lastName("User")
                .build());

        LoginRequest wrongReq = LoginRequest.builder()
                .email("wrong.pass@example.com")
                .password("WrongPassword")
                .build();

        assertThrows(BadCredentialsException.class, () -> authService.login(wrongReq));
    }

    @Test
    @DisplayName("Refresh token rotation works and detects reuse to revoke entire family")
    void testRefreshTokenRotationAndReuseDetection() {
        UserProfileDto userProfile = authService.register(RegisterRequest.builder()
                .email("rotate.user@example.com")
                .password("Pass12345!")
                .firstName("Rotate")
                .lastName("User")
                .build());

        AuthResponse initialAuth = authService.login(LoginRequest.builder()
                .email("rotate.user@example.com")
                .password("Pass12345!")
                .build());

        String initialRefreshToken = initialAuth.getRefreshToken();

        // 1. First Refresh (Rotation)
        AuthResponse rotatedAuth = authService.refreshToken(RefreshTokenRequest.builder()
                .refreshToken(initialRefreshToken)
                .build());

        assertThat(rotatedAuth.getRefreshToken()).isNotEqualTo(initialRefreshToken);
        assertThat(jwtService.validateToken(rotatedAuth.getAccessToken())).isTrue();

        // 2. Replay Attack: Reusing initialRefreshToken must fail and revoke the entire family
        assertThrows(BadCredentialsException.class, () -> {
            authService.refreshToken(RefreshTokenRequest.builder()
                    .refreshToken(initialRefreshToken)
                    .build());
        });

        // 3. The rotated token must now also be revoked due to family reuse detection
        assertThrows(BadCredentialsException.class, () -> {
            authService.refreshToken(RefreshTokenRequest.builder()
                    .refreshToken(rotatedAuth.getRefreshToken())
                    .build());
        });
    }

    @Test
    @DisplayName("Tenant switching validates membership and updates active JWT claims")
    void testTenantSwitching() {
        UserProfileDto userProfile = authService.register(RegisterRequest.builder()
                .email("switch.user@example.com")
                .password("Pass12345!")
                .firstName("Switch")
                .lastName("User")
                .build());

        AppUser user = userRepository.findById(userProfile.getId()).orElseThrow();

        // Assign to Tenant 1 as COMPANY_ADMIN
        TenantMembership m1 = TenantMembership.builder()
                .user(user)
                .status("ACTIVE")
                .roles(Set.of(adminRole))
                .build();
        m1.setTenantId(tenant1.getId());
        membershipRepository.save(m1);

        // Assign to Tenant 2 as EMPLOYEE
        TenantMembership m2 = TenantMembership.builder()
                .user(user)
                .status("ACTIVE")
                .roles(Set.of(employeeRole))
                .build();
        m2.setTenantId(tenant2.getId());
        membershipRepository.save(m2);

        // Login into Tenant 1
        AuthResponse loginResponse = authService.login(LoginRequest.builder()
                .email("switch.user@example.com")
                .password("Pass12345!")
                .tenantId(tenant1.getId())
                .build());

        assertThat(loginResponse.getActiveTenantId()).isEqualTo(tenant1.getId());
        assertThat(loginResponse.getRoles()).contains("COMPANY_ADMIN");

        // Switch to Tenant 2
        AuthResponse switchedResponse = authService.switchTenant(user.getId(), tenant2.getId());
        assertThat(switchedResponse.getActiveTenantId()).isEqualTo(tenant2.getId());
        assertThat(switchedResponse.getRoles()).contains("EMPLOYEE");

        // Attempt switch to an unauthorized random tenant must fail
        assertThrows(BadCredentialsException.class, () -> {
            authService.switchTenant(user.getId(), UUID.randomUUID());
        });
    }

    @Test
    @DisplayName("Suspended membership is rejected on tenant switch")
    void testSuspendedMembershipRejection() {
        UserProfileDto userProfile = authService.register(RegisterRequest.builder()
                .email("suspended@example.com")
                .password("Pass12345!")
                .firstName("Suspended")
                .lastName("User")
                .build());

        AppUser user = userRepository.findById(userProfile.getId()).orElseThrow();

        TenantMembership suspendedMembership = TenantMembership.builder()
                .user(user)
                .status("SUSPENDED")
                .roles(Set.of(employeeRole))
                .build();
        suspendedMembership.setTenantId(tenant1.getId());
        membershipRepository.save(suspendedMembership);

        assertThrows(BadCredentialsException.class, () -> {
            authService.switchTenant(user.getId(), tenant1.getId());
        });
    }
}
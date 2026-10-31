package com.hrmspro.modules.auth.service;

import com.hrmspro.config.security.JwtService;
import com.hrmspro.modules.auth.dto.*;
import com.hrmspro.modules.rbac.Role;
import com.hrmspro.modules.tenant.Tenant;
import com.hrmspro.modules.tenant.TenantMembership;
import com.hrmspro.modules.tenant.TenantMembershipRepository;
import com.hrmspro.modules.tenant.TenantRepository;
import com.hrmspro.modules.user.AppUser;
import com.hrmspro.modules.user.AppUserRepository;
import com.hrmspro.modules.user.RefreshToken;
import com.hrmspro.modules.user.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${app.jwt.refresh-token-expiration-ms:604800000}") // 7 days default
    private long refreshTokenExpirationMs;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public UserProfileDto register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("An account with this email address already exists");
        }

        AppUser user = AppUser.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .isActive(true)
                .isEmailVerified(false)
                .build();

        user = userRepository.save(user);

        return UserProfileDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .active(user.getIsActive())
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        AppUser user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BadCredentialsException("Account is disabled");
        }

        List<TenantMembership> memberships = membershipRepository.findByUserId(user.getId());

        TenantMembership activeMembership = null;
        if (request.getTenantId() != null) {
            activeMembership = memberships.stream()
                    .filter(m -> m.getTenantId().equals(request.getTenantId()) && "ACTIVE".equalsIgnoreCase(m.getStatus()))
                    .findFirst()
                    .orElseThrow(() -> new BadCredentialsException("No active membership found for the requested company"));
        } else if (!memberships.isEmpty()) {
            activeMembership = memberships.stream()
                    .filter(m -> "ACTIVE".equalsIgnoreCase(m.getStatus()))
                    .findFirst()
                    .orElse(null);
        }

        UUID activeTenantId = activeMembership != null ? activeMembership.getTenantId() : null;
        UUID activeMembershipId = activeMembership != null ? activeMembership.getId() : null;
        String tenantName = null;
        List<String> roleNames = Collections.emptyList();

        if (activeMembership != null) {
            roleNames = activeMembership.getRoles().stream()
                    .map(Role::getName)
                    .collect(Collectors.toList());
            tenantName = tenantRepository.findById(activeTenantId)
                    .map(Tenant::getName)
                    .orElse(null);
        }

        String accessToken = jwtService.generateAccessToken(user.getId(), activeTenantId, activeMembershipId, roleNames);

        // Issue new refresh token with fresh familyId
        UUID familyId = UUID.randomUUID();
        String rawRefreshToken = generateSecureToken();
        String tokenHash = JwtService.hashToken(rawRefreshToken);

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .tenantId(activeTenantId)
                .membership(activeMembership)
                .familyId(familyId)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusMillis(refreshTokenExpirationMs))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshTokenEntity);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(900L) // 15 mins
                .user(UserProfileDto.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .phone(user.getPhone())
                        .active(user.getIsActive())
                        .build())
                .activeTenantId(activeTenantId)
                .activeTenantName(tenantName)
                .roles(roleNames)
                .build();
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String tokenHash = JwtService.hashToken(request.getRefreshToken());

        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        UUID familyId = storedToken.getFamilyId();

        // Reuse Detection: If an already revoked token is used again, revoke the entire family immediately
        if (Boolean.TRUE.equals(storedToken.getRevoked())) {
            log.warn("Detected refresh token reuse attempt! Revoking entire family {}", familyId);
            List<RefreshToken> familyTokens = refreshTokenRepository.findByFamilyId(familyId);
            for (RefreshToken token : familyTokens) {
                token.setRevoked(true);
                token.setRevocationReason("REUSE_DETECTED");
            }
            refreshTokenRepository.saveAll(familyTokens);
            throw new BadCredentialsException("Refresh token was previously invalidated. Entire session terminated.");
        }

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            storedToken.setRevoked(true);
            storedToken.setRevocationReason("EXPIRED");
            refreshTokenRepository.save(storedToken);
            throw new BadCredentialsException("Refresh token has expired");
        }

        AppUser user = storedToken.getUser();
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BadCredentialsException("User account is inactive");
        }

        // Rotate token
        String newRawToken = generateSecureToken();
        String newTokenHash = JwtService.hashToken(newRawToken);

        storedToken.setRevoked(true);
        storedToken.setReplacedByHash(newTokenHash);
        storedToken.setRevocationReason("ROTATED");
        refreshTokenRepository.save(storedToken);

        RefreshToken newRefreshToken = RefreshToken.builder()
                .user(user)
                .tenantId(storedToken.getTenantId())
                .membership(storedToken.getMembership())
                .familyId(familyId)
                .tokenHash(newTokenHash)
                .expiresAt(Instant.now().plusMillis(refreshTokenExpirationMs))
                .revoked(false)
                .build();

        refreshTokenRepository.save(newRefreshToken);

        // Generate new access token
        List<String> roleNames = Collections.emptyList();
        String tenantName = null;
        UUID activeTenantId = storedToken.getTenantId();
        UUID activeMembershipId = storedToken.getMembership() != null ? storedToken.getMembership().getId() : null;

        if (storedToken.getMembership() != null) {
            TenantMembership membership = storedToken.getMembership();
            roleNames = membership.getRoles().stream()
                    .map(Role::getName)
                    .collect(Collectors.toList());
            tenantName = tenantRepository.findById(activeTenantId)
                    .map(Tenant::getName)
                    .orElse(null);
        }

        String accessToken = jwtService.generateAccessToken(user.getId(), activeTenantId, activeMembershipId, roleNames);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRawToken)
                .tokenType("Bearer")
                .expiresIn(900L)
                .user(UserProfileDto.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .phone(user.getPhone())
                        .active(user.getIsActive())
                        .build())
                .activeTenantId(activeTenantId)
                .activeTenantName(tenantName)
                .roles(roleNames)
                .build();
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        if (request != null && request.getRefreshToken() != null) {
            String tokenHash = JwtService.hashToken(request.getRefreshToken());
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
                token.setRevoked(true);
                token.setRevocationReason("LOGOUT");
                refreshTokenRepository.save(token);
            });
        }
    }

    @Transactional
    public AuthResponse switchTenant(UUID userId, UUID targetTenantId) {
        TenantMembership targetMembership = membershipRepository.findByUserIdAndTenantId(userId, targetTenantId)
                .orElseThrow(() -> new BadCredentialsException("No membership found in the target company"));

        if (!"ACTIVE".equalsIgnoreCase(targetMembership.getStatus())) {
            throw new BadCredentialsException("Membership in target company is not active (Status: " + targetMembership.getStatus() + ")");
        }

        AppUser user = targetMembership.getUser();
        List<String> roleNames = targetMembership.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toList());

        Tenant tenant = tenantRepository.findById(targetTenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));

        String accessToken = jwtService.generateAccessToken(user.getId(), targetTenantId, targetMembership.getId(), roleNames);

        // Issue rotated refresh token bound to new active tenant
        UUID familyId = UUID.randomUUID();
        String rawRefreshToken = generateSecureToken();
        String tokenHash = JwtService.hashToken(rawRefreshToken);

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .tenantId(targetTenantId)
                .membership(targetMembership)
                .familyId(familyId)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusMillis(refreshTokenExpirationMs))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshTokenEntity);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(900L)
                .user(UserProfileDto.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .phone(user.getPhone())
                        .active(user.getIsActive())
                        .build())
                .activeTenantId(targetTenantId)
                .activeTenantName(tenant.getName())
                .roles(roleNames)
                .build();
    }

    @Transactional(readOnly = true)
    public List<TenantMembershipDto> getUserMemberships(UUID userId) {
        List<TenantMembership> memberships = membershipRepository.findByUserId(userId);
        List<TenantMembershipDto> result = new ArrayList<>();

        for (TenantMembership m : memberships) {
            Tenant tenant = tenantRepository.findById(m.getTenantId()).orElse(null);
            result.add(TenantMembershipDto.builder()
                    .membershipId(m.getId())
                    .tenantId(m.getTenantId())
                    .tenantName(tenant != null ? tenant.getName() : null)
                    .tenantSubdomain(tenant != null ? tenant.getSubdomain() : null)
                    .companyCode(tenant != null ? tenant.getCompanyCode() : null)
                    .status(m.getStatus())
                    .designation(m.getDesignation())
                    .employeeCode(m.getEmployeeCode())
                    .roles(m.getRoles().stream().map(Role::getName).collect(Collectors.toList()))
                    .build());
        }

        return result;
    }

    private String generateSecureToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
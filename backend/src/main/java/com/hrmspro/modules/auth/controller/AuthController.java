package com.hrmspro.modules.auth.controller;

import com.hrmspro.common.response.ApiResponse;
import com.hrmspro.config.security.UserPrincipal;
import com.hrmspro.modules.auth.dto.*;
import com.hrmspro.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user registration, authentication, token rotation, and tenant switching")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register new global user account")
    public ResponseEntity<ApiResponse<UserProfileDto>> register(@Valid @RequestBody RegisterRequest request) {
        UserProfileDto response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Account successfully registered"));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and issue access and refresh tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token and issue fresh access token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed successfully"));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke active refresh token session")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
    }

    @PostMapping("/switch-tenant")
    @Operation(summary = "Switch active tenant session and issue updated JWT with tenant roles")
    public ResponseEntity<ApiResponse<AuthResponse>> switchTenant(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SwitchTenantRequest request
    ) {
        AuthResponse response = authService.switchTenant(principal.getUserId(), request.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(response, "Switched tenant successfully"));
    }

    @GetMapping("/memberships")
    @Operation(summary = "List all companies and memberships accessible by current user")
    public ResponseEntity<ApiResponse<List<TenantMembershipDto>>> getMyMemberships(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<TenantMembershipDto> memberships = authService.getUserMemberships(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(memberships, "Retrieved user memberships"));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile and active tenant context")
    public ResponseEntity<ApiResponse<UserPrincipal>> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(principal, "Current authenticated user"));
    }
}
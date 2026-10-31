# Changelog

All notable changes to the HRMS Pro project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]
### Added
- Repository initialization with Git and root .gitignore.
- Full-stack monorepo scaffolding (backend, web, mobile).
- Architecture audit document docs/PHASE_1A_ARCHITECTURE_AUDIT.md.
- Multi-tenant RBAC schema refactoring migration V2__multi_tenant_rbac_schema.sql and JPA entities.
- Phase 1C Secure Authentication & Tenant Context:
  - Spring Security stateless filter chain with JwtAuthenticationFilter.
  - BCrypt password hashing (strength 12).
  - Short-lived HMAC-SHA256 JWT access tokens (15 mins) with 	enant_id, membership_id, and oles.
  - Opaque refresh tokens stored as SHA-256 hashes with family tracking, rotation, and replay detection.
  - Dynamic tenant switching endpoint /api/v1/auth/switch-tenant.
  - Transaction-local PostgreSQL RLS setter via TenantContextAspect (set_config('app.current_tenant_id', ..., true)).
  - Auth REST controller: /register, /login, /refresh, /logout, /switch-tenant, /memberships, /me.
  - Verified test suite: 10/10 tests passed (AuthenticationAndTenantContextTests, MultiTenantSchemaAndRbacTests).
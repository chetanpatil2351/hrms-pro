# Changelog

All notable changes to the HRMS Pro project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]
### Added
- Repository initialization with Git and root .gitignore.
- Full-stack monorepo scaffolding (backend, web, mobile).
- Architecture audit document docs/PHASE_1A_ARCHITECTURE_AUDIT.md.
- Phase 1B schema refactoring migration V2__multi_tenant_rbac_schema.sql and JPA entities.
- Phase 1C Authentication and JWT Token Lifecycle (docs/PHASE_1C_AUTHENTICATION.md).
- Phase 1D Company Onboarding & Tenant Management (docs/PHASE_1D_COMPANY_ONBOARDING.md):
  - Flyway migration V3__tenant_management_and_invitations.sql.
  - Company onboarding endpoint: creator automatically becomes COMPANY_ADMIN and provisions initial Head Office branch.
  - Company profile management (GET /tenants/current, PUT /tenants/current, GET /tenants/my-companies).
  - Branch & Department management APIs with cross-tenant isolation enforcement.
  - Secure member invitation foundation with single-use cryptographic tokens.
  - Subscription capacity and max employee limit checks.
  - Automated test suite CompanyOnboardingAndManagementTests passing 100%.
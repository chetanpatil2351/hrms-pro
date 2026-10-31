# Changelog

All notable changes to the HRMS Pro project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]
### Added
- Repository initialization with Git and root .gitignore.
- Full-stack monorepo scaffolding (backend, web, mobile).
- Architecture audit document docs/PHASE_1A_ARCHITECTURE_AUDIT.md.
- Phase 1B schema refactoring migration V2__multi_tenant_rbac_schema.sql:
  - Decoupled pp_users global identity from company tenancy.
  - 	enant_memberships supporting multiple companies per user with distinct roles and statuses.
  - Granular RBAC tables (oles, permissions, membership_roles, ole_permissions).
  - Hierarchical ranches and departments.
  - Modern efresh_tokens table with token families, rotation, and reuse detection.
  - Strict default-deny PostgreSQL Row Level Security (RLS) policies with FORCE ROW LEVEL SECURITY.
- JPA entities and repositories for all new tenancy and RBAC models.
- Automated tests in MultiTenantSchemaAndRbacTests validating multi-tenancy constraints and token rotation.
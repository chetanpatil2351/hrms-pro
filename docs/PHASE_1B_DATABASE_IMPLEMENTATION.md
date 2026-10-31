# HRMS Pro — Phase 1B: Multi-Tenant RBAC & Database Implementation

**Document Version**: 1.0.0  
**Phase**: 1B — Database Architecture, Schema Migration & Row Level Security  
**Date**: October 31, 2026  
**Status**: Implemented & Verified with Automated Tests  

---

## 1. Schema Overview & Architecture

Phase 1B refactors the database architecture from a single-tenant coupled user model into a decoupled, commercial-grade multi-tenant RBAC platform.

### Core Capabilities Achieved
1. **Global User Identity**: A single global user record (pp_users) can belong to multiple tenants/companies simultaneously.
2. **Context-Specific Roles**: A user can hold different roles in different companies (e.g. COMPANY_ADMIN in Company A, EMPLOYEE in Company B).
3. **Multi-Unit Hierarchy**: Full support for ranches and departments under each tenant.
4. **Strict PostgreSQL Row-Level Security (RLS)**: Enforces default-deny tenant isolation directly in the database engine using FORCE ROW LEVEL SECURITY.
5. **Modern Refresh Token Lifecycles**: Supports SHA-256 token hashes, amily_id tracking, rotation chaining (eplaced_by_hash), and reuse detection.

---

## 2. Entity-Relationship (ER) Diagram

`mermaid
erDiagram
    app_users ||--o{ tenant_memberships : "belongs to"
    tenants ||--o{ tenant_memberships : "has members"
    tenants ||--o{ branches : "has"
    branches ||--o{ departments : "contains"
    branches ||--o{ tenant_memberships : "assigned to"
    departments ||--o{ tenant_memberships : "assigned to"
    
    tenant_memberships ||--o{ membership_roles : "assigned"
    roles ||--o{ membership_roles : "grants"
    
    roles ||--o{ role_permissions : "contains"
    permissions ||--o{ role_permissions : "granted to"
    
    app_users ||--o{ refresh_tokens : "owns"
    tenant_memberships ||--o{ refresh_tokens : "scoped to"

    app_users {
        UUID id PK
        VARCHAR email UK
        VARCHAR password_hash
        VARCHAR first_name
        VARCHAR last_name
        BOOLEAN is_active
        BOOLEAN is_email_verified
        TIMESTAMPTZ created_at
    }

    tenants {
        UUID id PK
        VARCHAR name
        VARCHAR subdomain UK
        VARCHAR company_code UK
        VARCHAR subscription_plan
        INT max_employees
        BOOLEAN is_active
        TIMESTAMPTZ created_at
    }

    tenant_memberships {
        UUID id PK
        UUID user_id FK
        UUID tenant_id FK
        UUID branch_id FK
        UUID department_id FK
        VARCHAR status
        VARCHAR designation
        VARCHAR employee_code
        TIMESTAMPTZ joined_at
    }

    roles {
        UUID id PK
        UUID tenant_id FK
        VARCHAR name
        BOOLEAN is_system_role
    }

    permissions {
        UUID id PK
        VARCHAR code UK
        VARCHAR module
        TEXT description
    }

    refresh_tokens {
        UUID id PK
        UUID user_id FK
        UUID tenant_id FK
        UUID membership_id FK
        UUID family_id
        VARCHAR token_hash UK
        VARCHAR replaced_by_hash
        TIMESTAMPTZ expires_at
        BOOLEAN revoked
    }
`

---

## 3. Flyway Migration Details (V2__multi_tenant_rbac_schema.sql)

- **New Tables**: pp_users, ranches, departments, 	enant_memberships, oles, permissions, membership_roles, ole_permissions.
- **Refactored Table**: efresh_tokens recreated with token family, rotation tracking, and expiration metadata.
- **Data Preservation Strategy**:
  - The migration checks for the legacy users table via an idempotent PL/pgSQL block:
    `sql
    DO 
    BEGIN
        IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'users') THEN
            INSERT INTO app_users (...) SELECT DISTINCT ON (email) ... FROM users ON CONFLICT DO NOTHING;
            INSERT INTO tenant_memberships (...) SELECT ... FROM users ... ON CONFLICT DO NOTHING;
            INSERT INTO membership_roles (...) SELECT ... FROM users ... ON CONFLICT DO NOTHING;
        END IF;
    END ;
    `
  - Preserves user identity, tenant association, and maps previous string roles directly into membership_roles.

---

## 4. PostgreSQL Row Level Security (RLS) Policy Design

### 4.1 Strict Default-Deny Architecture
All tenant-isolated tables (ranches, departments, 	enant_memberships, efresh_tokens) are configured with:
`sql
ALTER TABLE <table_name> ENABLE ROW LEVEL SECURITY;
ALTER TABLE <table_name> FORCE ROW LEVEL SECURITY;
`

Policies enforce that current_setting('app.current_tenant_id', true) must be **both non-null and non-empty**, matching 	enant_id:
`sql
CREATE POLICY rls_tenant_memberships_tenant_isolation ON tenant_memberships
    FOR ALL
    USING (
        current_setting('app.current_tenant_id', true) IS NOT NULL
        AND current_setting('app.current_tenant_id', true) <> ''
        AND tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::UUID
    )
    WITH CHECK (
        current_setting('app.current_tenant_id', true) IS NOT NULL
        AND current_setting('app.current_tenant_id', true) <> ''
        AND tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::UUID
    );
`

### 4.2 Runtime Database Role Requirements & Limitations
1. **Limitation of FORCE ROW LEVEL SECURITY**: In PostgreSQL, FORCE ROW LEVEL SECURITY prevents table owners from bypassing RLS policies, **but it does NOT constrain superusers or roles granted BYPASSRLS**.
2. **Production Database Role Rule**:
   - The Spring Boot application must **never** connect using the Supabase postgres superuser.
   - A dedicated application user (e.g. hrms_app_user) must be granted SELECT, INSERT, UPDATE, DELETE without SUPERUSER or BYPASSRLS privileges.

---

## 5. Automated Test Results

The suite in MultiTenantSchemaAndRbacTests was executed via mvn clean test against Spring Boot 3.3.4:

| Test Case | Description | Result |
| :--- | :--- | :--- |
| 	estSingleUserMultipleTenantsWithDifferentRoles | Verifies a single global user belongs to multiple tenants with independent roles (COMPANY_ADMIN vs EMPLOYEE) and different branch assignments. | **PASSED** |
| 	estDuplicateMembershipConstraint | Enforces the compound UNIQUE(user_id, tenant_id) constraint preventing duplicate memberships in the same tenant. | **PASSED** |
| 	estRefreshTokenRotationFoundation | Tests token family chaining, hash storage, and revocation tracking during rotation. | **PASSED** |
| contextLoads | Full Spring Boot context and JPA repository bootstrapping test. | **PASSED** |

**Summary**: 4 Tests Run, 0 Failures, 0 Errors, 0 Skipped (BUILD SUCCESS).

---

## 6. Known Limitations & Next Steps
- **Direct PostgreSQL RLS Automated Execution**: Local testing runs against H2 PostgreSQL mode. Live RLS policy enforcement with PostgreSQL session variables (set_config('app.current_tenant_id', ..., true)) must be validated against the Supabase PostgreSQL dev instance or Testcontainers when Docker is available.
- **Ready for Phase 1C**: Authentication endpoints, JWT generation, password hashing (Argon2/BCrypt), and tenant context resolution filters.
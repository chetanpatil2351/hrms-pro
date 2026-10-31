# HRMS Pro — Phase 1A: Architecture & PostgreSQL Security Audit

**Document Version**: 1.0.0  
**Audit Scope**: Multi-Tenant Data Architecture, PostgreSQL RLS, Session Leakage in HikariCP, Authentication & Token Lifecycles  
**Auditor**: Senior Software Architect & Security Engineer  
**Date**: October 31, 2026  

---

## Executive Summary

HRMS Pro has established its foundational monorepo scaffold across Spring Boot 3.3.4 (Java 21), React 18 / Vite, and React Native / Expo. Before implementing authentication endpoints and business services, this Phase 1A audit evaluates:
1. **Multi-tenant schema flexibility** (handling single user identities across multiple companies).
2. **PostgreSQL Row Level Security (RLS) enforcement** and **HikariCP connection-leak vulnerabilities**.
3. **Authentication, Token Lifecycles, and Revocation Architecture**.
4. **Defense-in-depth security** across CORS, secrets, and authorization.

---

## 1. Existing Architecture Findings

### 1.1 Current Schema Assessment (V1__init_schema.sql)
- The current V1 migration couples user identity directly to a specific tenant:
  `sql
  CREATE TABLE users (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
      email VARCHAR(255) NOT NULL,
      ...
      role VARCHAR(50) NOT NULL,
      CONSTRAINT uk_tenant_user_email UNIQUE (tenant_id, email)
  );
  `
- **Findings**:
  - ❌ **No Multi-Company User Identity**: A single physical user (e.g. an external accountant, consultant, or multi-franchise employee) cannot have one global login credential across multiple companies. To access another company, duplicate user accounts and separate passwords would be needed.
  - ❌ **No Granular Role/Permission Separation**: Roles are hardcoded as single strings on the users table instead of decoupled, composable roles and permissions assigned per membership.
  - ❌ **Missing Organizational Hierarchy**: Branches and Departments are absent, preventing branch-level access control (BRANCH_MANAGER).

### 1.2 PostgreSQL RLS Review
The existing RLS policy reads:
`sql
CREATE POLICY tenant_isolation_policy_users ON users
    AS PERMISSIVE FOR ALL
    USING (
        current_setting('app.current_tenant_id', true) IS NULL 
        OR current_setting('app.current_tenant_id', true) = ''
        OR tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::UUID
    );
`
- **Critical Finding**:
  - 🚨 **Fails Safe Violation (Permissive Fallback)**: The clause current_setting('app.current_tenant_id', true) IS NULL OR ... = '' means that if tenant context is **omitted or forgotten**, the policy **returns all records across all tenants**! This completely breaks tenant isolation if an unauthenticated or misconfigured query runs.

---

## 2. Critical Security Risks & Mitigations

### 2.1 Critical Risk 1: RLS Bypass via Permissive Policy Fallback
- **Severity**: **Critical (CVSS 9.8)**
- **Risk**: An attacker invoking an endpoint where pp.current_tenant_id was not explicitly set would receive rows for every customer in the SaaS platform.
- **Remediation**:
  - Invert the policy logic to **Default Deny**:
    `sql
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::UUID
    );
    `
  - For Super Admin operations that deliberately span tenants, use a dedicated, strictly audited superadmin role or an explicit bypass session variable (pp.is_super_admin = 'true').

### 2.2 Critical Risk 2: Connection Pool Session Variable Leakage (HikariCP)
- **Severity**: **High (CVSS 8.5)**
- **Risk**: Spring Boot uses HikariCP connection pooling. If Request A sets SET LOCAL app.current_tenant_id = 'tenant-123', but connection reuse occurs without cleanup, Request B might inherit Request A's tenant if transactions are improperly scoped or if non-transactional queries execute.
- **Remediation**:
  1. Use PostgreSQL set_config('app.current_tenant_id', :tenantId, true) with is_local = true inside an active transaction (@Transactional), ensuring the setting automatically discards at transaction commit/rollback.
  2. Implement an AOP Around advice or JPA ConnectionPreparator that clears RESET app.current_tenant_id whenever a connection returns to HikariCP.

### 2.3 Critical Risk 3: Supabase Database Role & RLS Bypass
- **Severity**: **High (CVSS 8.2)**
- **Risk**: In PostgreSQL, table owners and superusers bypass RLS by default. If the Spring Boot backend connects using the postgres superuser role in Supabase, RLS policies will be bypassed entirely unless FORCE ROW LEVEL SECURITY is applied.
- **Remediation**:
  1. Apply ALTER TABLE <table_name> FORCE ROW LEVEL SECURITY; on all tenant-protected tables.
  2. Connect using a dedicated non-superuser role (e.g., hrms_app_user) with limited DML permissions.

---

## 3. Required Database Schema Corrections (Phase 1B Migration)

To support multiple companies per user, different roles per company, branches, and fine-grained permissions, we will replace the monolithic structure with a decoupled identity and membership architecture:

`
+----------------+       1:N       +---------------------+       N:1       +----------------+
|   app_users    | <-------------- | tenant_memberships  | --------------> |    tenants     |
+----------------+                 +---------------------+                 +----------------+
        |                                     |                                    |
        | 1:N                                 | 1:N                                | 1:N
        v                                     v                                    v
+----------------+                 +---------------------+                 +----------------+
| refresh_tokens |                 |  membership_roles   |                 |    branches    |
+----------------+                 +---------------------+                 +----------------+
                                              |                                    |
                                              | N:1                                | 1:N
                                              v                                    v
                                   +---------------------+                 +----------------+
                                   |        roles        |                 |  departments   |
                                   +---------------------+                 +----------------+
                                              |
                                              | 1:N
                                              v
                                   +---------------------+
                                   |  role_permissions   |
                                   +---------------------+
                                              |
                                              | N:1
                                              v
                                   +---------------------+
                                   |     permissions     |
                                   +---------------------+
`

### Table Definitions & Responsibilities

1. **pp_users** (Global Identity)
   - id UUID PRIMARY KEY, email VARCHAR UNIQUE, phone VARCHAR, password_hash VARCHAR, irst_name, last_name, is_active, is_email_verified.
2. **	enants** (Organization / Company)
   - id UUID PRIMARY KEY, 
ame, subdomain UNIQUE, company_code UNIQUE, subscription_tier, max_employees, is_active.
3. **ranches** & **departments** (Multi-Unit Structure)
   - Scoped with 	enant_id referencing 	enants(id).
4. **	enant_memberships** (User Company Association)
   - id UUID PRIMARY KEY, user_id REFERENCES app_users(id), 	enant_id REFERENCES tenants(id), ranch_id REFERENCES branches(id), department_id REFERENCES departments(id), status VARCHAR (ACTIVE, SUSPENDED, INVITED), UNIQUE(user_id, tenant_id).
5. **oles** & **permissions** (RBAC)
   - System standard roles (SUPER_ADMIN, COMPANY_ADMIN, HR_MANAGER, BRANCH_MANAGER, ATTENDANCE_MANAGER, PAYROLL_MANAGER, ACCOUNTANT, EMPLOYEE).
   - Granular permissions (ttendance:read, ttendance:write, payroll:approve, etc.).
6. **efresh_tokens**
   - Storing SHA-256 token hashes, family IDs for reuse detection, expiration timestamps, and revocation flags.

---

## 4. PostgreSQL RLS Implementation Strategy

### 4.1 Strict Default-Deny Policies
`sql
-- Ensure RLS is enforced even for table owners
ALTER TABLE tenant_memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_memberships FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_memberships ON tenant_memberships
    AS RESTRICTIVE
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::UUID
    );
`

### 4.2 Spring Boot Tenant Context Enforcer
`java
// ThreadLocal Context holder
public class TenantContext {
    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();
    public static void setTenantId(UUID tenantId) { CURRENT_TENANT.set(tenantId); }
    public static UUID getTenantId() { return CURRENT_TENANT.get(); }
    public static void clear() { CURRENT_TENANT.remove(); }
}
`
Inside transaction boundary (Hibernate StatementInspector or Spring TransactionSynchronizationManager):
`sql
SELECT set_config('app.current_tenant_id', :tenantId, true);
`

---

## 5. Recommended Authentication Architecture

### 5.1 Registration & Authentication Flow

1. **User Login (/api/v1/auth/login)**:
   - Accepts email + password.
   - Validates credentials against pp_users using BCryptPasswordEncoder (strength 12) or Argon2.
   - Retrieves active 	enant_memberships.
   - If user has **1 company**: issues Access Token with embedded 	enant_id, membership_id, and oles.
   - If user has **multiple companies**: issues a short-lived Selection Token allowing the user to select the active company, or logs into their default company with an endpoint to switch tenants (/api/v1/auth/switch-tenant).

2. **Access Token (Short-Lived JWT)**:
   - Expiration: **15 minutes**.
   - Claims: sub (User UUID), 	enant_id (Active Tenant UUID), membership_id (Membership UUID), oles (["COMPANY_ADMIN"]), permissions (["attendance:write", ...]).

3. **Refresh Token Rotation & Reuse Detection**:
   - Expiration: **7 days**, HTTP-only, secure, SameSite cookie or protected JSON payload.
   - Database stores 	oken_hash = SHA-256(rawToken).
   - Each token belongs to a 	oken_family_id.
   - When refreshed:
     - Old refresh token is marked as evoked = true.
     - A new refresh token is issued and stored in the same family.
     - **Reuse Detection**: If a revoked token is presented, the entire 	oken_family_id is immediately invalidated, and all active sessions for that user/device are terminated (indicating token theft).

---

## 6. Security Review Checklist & Hardening

| Check | Status | Action Item |
| :--- | :--- | :--- |
| **JWT Secret Handling** | Verified | Secret passed via JWT_SECRET environment variable; fallback test secret disabled in production profile. |
| **CORS Policy** | Pending | Restrict allowed origins to designated web dashboard domain & mobile schemes; block wildcard * with credentials. |
| **API Authorization** | Planned | Spring Security method security @PreAuthorize("hasAuthority('attendance:write')"). |
| **Exception Handling** | In Place | Standard GlobalExceptionHandler returning ApiResponse<T> with field validations without leaking internal stack traces. |
| **Secrets in Git** | Clear | .gitignore verified blocking .env, *.pem, *.key, secrets.yaml. Zero credentials committed. |
| **Supabase Service Key** | Clear | Service role key strictly prohibited from frontend & mobile bundles. Backend uses connection URL via environment variable. |

---

## 7. Migration Roadmap

1. **Phase 1B**: Create V2__multi_tenant_rbac_schema.sql to implement the decoupled pp_users, 	enants, 	enant_memberships, oles, and permissions tables.
2. **Phase 1C**: Implement Spring Security JWT filter, Argon2/BCrypt hashing, tenant context interceptor, and Auth Controller (/login, /register, /refresh, /switch-tenant, /me).
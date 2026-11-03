-- ====================================================================
-- HRMS Pro Migration V2: Multi-Tenant RBAC & Identity Decoupling
-- Description:
--   1. Create decoupled global app_users (preserving data from legacy users).
--   2. Create branches and departments.
--   3. Create tenant_memberships with status (ACTIVE, INVITED, etc.).
--   4. Create roles, permissions, membership_roles, role_permissions.
--   5. Refactor refresh_tokens to support token family, rotation & reuse detection.
--   6. Enforce strict default-deny PostgreSQL Row Level Security (RLS).
-- ====================================================================

-- 1. App Users (Global Identity)
CREATE TABLE IF NOT EXISTS app_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(50),
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_app_users_email ON app_users(email);

-- 2. Branches
CREATE TABLE IF NOT EXISTS branches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) NOT NULL,
    address TEXT,
    city VARCHAR(100),
    state VARCHAR(100),
    country VARCHAR(100) DEFAULT 'India',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_branch_code UNIQUE (tenant_id, code)
);

CREATE INDEX IF NOT EXISTS idx_branches_tenant ON branches(tenant_id);

-- 3. Departments
CREATE TABLE IF NOT EXISTS departments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_dept_code UNIQUE (tenant_id, code)
);

CREATE INDEX IF NOT EXISTS idx_departments_tenant ON departments(tenant_id);

-- 4. Roles & Permissions (RBAC)
CREATE TABLE IF NOT EXISTS roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE, -- NULL for system-wide standard roles
    name VARCHAR(100) NOT NULL,
    description TEXT,
    is_system_role BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_role_name UNIQUE (tenant_id, name)
);

CREATE INDEX IF NOT EXISTS idx_roles_tenant ON roles(tenant_id);

CREATE TABLE IF NOT EXISTS permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(100) NOT NULL UNIQUE, -- e.g. attendance:read, payroll:approve
    module VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- Seed System Standard Roles
INSERT INTO roles (id, tenant_id, name, description, is_system_role)
VALUES 
    ('00000000-0000-0000-0000-000000000001', NULL, 'SUPER_ADMIN', 'Platform Super Administrator', TRUE),
    ('00000000-0000-0000-0000-000000000002', NULL, 'COMPANY_ADMIN', 'Company Administrator / Owner', TRUE),
    ('00000000-0000-0000-0000-000000000003', NULL, 'HR_MANAGER', 'HR Manager', TRUE),
    ('00000000-0000-0000-0000-000000000004', NULL, 'BRANCH_MANAGER', 'Branch Operations Manager', TRUE),
    ('00000000-0000-0000-0000-000000000005', NULL, 'ATTENDANCE_MANAGER', 'Attendance Officer', TRUE),
    ('00000000-0000-0000-0000-000000000006', NULL, 'PAYROLL_MANAGER', 'Payroll Specialist', TRUE),
    ('00000000-0000-0000-0000-000000000007', NULL, 'ACCOUNTANT', 'Accountant', TRUE),
    ('00000000-0000-0000-0000-000000000008', NULL, 'EMPLOYEE', 'Standard Employee', TRUE)
ON CONFLICT DO NOTHING;

-- 5. Tenant Memberships (User-Company Association)
CREATE TABLE IF NOT EXISTS tenant_memberships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    department_id UUID REFERENCES departments(id) ON DELETE SET NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE', -- INVITED, ACTIVE, SUSPENDED, REVOKED
    designation VARCHAR(150),
    employee_code VARCHAR(50),
    joined_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_tenant_membership UNIQUE (user_id, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_memberships_user ON tenant_memberships(user_id);
CREATE INDEX IF NOT EXISTS idx_memberships_tenant ON tenant_memberships(tenant_id);

CREATE TABLE IF NOT EXISTS membership_roles (
    membership_id UUID NOT NULL REFERENCES tenant_memberships(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (membership_id, role_id)
);

-- 6. Safe Data Migration from legacy 'users' table if populated
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'users'
    ) THEN

        -- Insert distinct users into app_users
        INSERT INTO app_users (
            id, email, phone, password_hash, first_name, last_name,
            is_active, is_email_verified, created_at, updated_at
        )
        SELECT DISTINCT ON (email)
            id, email, phone, password_hash, first_name, last_name,
            is_active, is_email_verified, created_at, updated_at
        FROM users
        ORDER BY email, created_at
        ON CONFLICT (email) DO NOTHING;

        -- Create memberships for existing users
        INSERT INTO tenant_memberships (
            id, user_id, tenant_id, status, created_at, updated_at
        )
        SELECT
            u.id,
            au.id,
            u.tenant_id,
            'ACTIVE',
            u.created_at,
            u.updated_at
        FROM users u
        JOIN app_users au ON au.email = u.email
        ON CONFLICT (user_id, tenant_id) DO NOTHING;

        -- Map legacy roles to membership_roles
        INSERT INTO membership_roles (membership_id, role_id)
        SELECT tm.id, r.id
        FROM users u
        JOIN app_users au ON au.email = u.email
        JOIN tenant_memberships tm
            ON tm.user_id = au.id
           AND tm.tenant_id = u.tenant_id
        JOIN roles r
            ON r.name = u.role
           AND r.tenant_id IS NULL
        ON CONFLICT DO NOTHING;

    END IF;
END;
$$;

-- 7. Modernized Refresh Tokens Table
-- Drops legacy refresh_tokens safely and recreates with token family & reuse detection attributes
DROP TABLE IF EXISTS refresh_tokens CASCADE;

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    membership_id UUID REFERENCES tenant_memberships(id) ON DELETE CASCADE,
    family_id UUID NOT NULL DEFAULT gen_random_uuid(),
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    replaced_by_hash VARCHAR(255),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revocation_reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_family ON refresh_tokens(family_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_tenant ON refresh_tokens(tenant_id);

-- 8. PostgreSQL Row Level Security (RLS) Implementation
-- Strict Default-Deny: If app.current_tenant_id is missing, empty, or invalid, ZERO rows are returned.

ALTER TABLE branches ENABLE ROW LEVEL SECURITY;
ALTER TABLE branches FORCE ROW LEVEL SECURITY;

ALTER TABLE departments ENABLE ROW LEVEL SECURITY;
ALTER TABLE departments FORCE ROW LEVEL SECURITY;

ALTER TABLE tenant_memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_memberships FORCE ROW LEVEL SECURITY;

ALTER TABLE refresh_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_tokens FORCE ROW LEVEL SECURITY;

-- Dynamic RLS Policies: USING controls visibility, WITH CHECK validates data ingestion
-- Policy: branches
CREATE POLICY rls_branches_tenant_isolation ON branches
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

-- Policy: departments
CREATE POLICY rls_departments_tenant_isolation ON departments
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

-- Policy: tenant_memberships
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

-- Policy: refresh_tokens (scoped per tenant if tenant_id is set)
CREATE POLICY rls_refresh_tokens_tenant_isolation ON refresh_tokens
    FOR ALL
    USING (
        tenant_id IS NULL OR (
            current_setting('app.current_tenant_id', true) IS NOT NULL
            AND current_setting('app.current_tenant_id', true) <> ''
            AND tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::UUID
        )
    )
    WITH CHECK (
        tenant_id IS NULL OR (
            current_setting('app.current_tenant_id', true) IS NOT NULL
            AND current_setting('app.current_tenant_id', true) <> ''
            AND tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::UUID
        )
    );
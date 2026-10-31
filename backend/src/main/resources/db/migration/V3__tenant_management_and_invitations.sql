-- ====================================================================
-- HRMS Pro Migration V3: Tenant Management Expansion & Invitation Foundation
-- ====================================================================

-- 1. Extend tenants table with company profile fields
ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS legal_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS address TEXT,
    ADD COLUMN IF NOT EXISTS city VARCHAR(100),
    ADD COLUMN IF NOT EXISTS state VARCHAR(100),
    ADD COLUMN IF NOT EXISTS country VARCHAR(100) DEFAULT 'India',
    ADD COLUMN IF NOT EXISTS postal_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS industry VARCHAR(100),
    ADD COLUMN IF NOT EXISTS website VARCHAR(255),
    ADD COLUMN IF NOT EXISTS logo_url TEXT,
    ADD COLUMN IF NOT EXISTS timezone VARCHAR(50) DEFAULT 'Asia/Kolkata',
    ADD COLUMN IF NOT EXISTS subscription_status VARCHAR(50) DEFAULT 'ACTIVE'; -- TRIAL, ACTIVE, EXPIRED, SUSPENDED

-- 2. Tenant Invitations Table
CREATE TABLE IF NOT EXISTS tenant_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    department_id UUID REFERENCES departments(id) ON DELETE SET NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    invited_by UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    designation VARCHAR(150),
    employee_code VARCHAR(50),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, ACCEPTED, EXPIRED, REVOKED
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_invitations_tenant ON tenant_invitations(tenant_id);
CREATE INDEX IF NOT EXISTS idx_invitations_email ON tenant_invitations(email);
CREATE INDEX IF NOT EXISTS idx_invitations_token_hash ON tenant_invitations(token_hash);

-- 3. Row Level Security for Tenant Invitations
ALTER TABLE tenant_invitations ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_invitations FORCE ROW LEVEL SECURITY;

CREATE POLICY rls_tenant_invitations_tenant_isolation ON tenant_invitations
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
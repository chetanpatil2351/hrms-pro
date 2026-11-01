-- V10__payroll_settings_schema.sql
-- Tenant-level payroll settings (e.g. proration policy)

CREATE TABLE IF NOT EXISTS payroll_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    proration_policy VARCHAR(20) NOT NULL DEFAULT 'CALENDAR_DAYS',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_payroll_settings_tenant UNIQUE (tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_payroll_settings_tenant ON payroll_settings(tenant_id);

ALTER TABLE payroll_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE payroll_settings FORCE ROW LEVEL SECURITY;

CREATE POLICY rls_payroll_settings_tenant ON payroll_settings
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


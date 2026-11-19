-- HRMS Pro Migration V19_1: Salary Advances table

-- Table to store salary advance requests and tracking
CREATE UNIQUE INDEX IF NOT EXISTS ux_employees_tenant_id ON employees (tenant_id, id);

CREATE TABLE IF NOT EXISTS salary_advances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL,
    CONSTRAINT fk_salary_advance_employee FOREIGN KEY (tenant_id, employee_id) REFERENCES employees (tenant_id, id) ON DELETE RESTRICT,
    requested_amount NUMERIC(15,2) NOT NULL,
    approved_amount NUMERIC(15,2),
    status VARCHAR(20) NOT NULL,
    request_date TIMESTAMPTZ NOT NULL,
    approved_by UUID REFERENCES app_users(id) ON DELETE SET NULL,
    decision_date TIMESTAMPTZ,
    repayment_start_date DATE,
    installment_count INT,
    installment_amount NUMERIC(15,2),
    outstanding_balance NUMERIC(15,2),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_salary_advances_tenant_id UNIQUE (tenant_id, id)
);

-- Indexes for fast look‑ups (following project conventions)
CREATE INDEX IF NOT EXISTS idx_salary_advances_tenant ON salary_advances (tenant_id);
CREATE INDEX IF NOT EXISTS idx_salary_advances_employee ON salary_advances (employee_id);
CREATE INDEX IF NOT EXISTS idx_salary_advances_status ON salary_advances (status);

-- Enable Row‑Level Security (consistent with V10 and V13)
ALTER TABLE salary_advances ENABLE ROW LEVEL SECURITY;
ALTER TABLE salary_advances FORCE ROW LEVEL SECURITY;

-- RLS policy for tenant isolation
CREATE POLICY rls_salary_advances_tenant ON salary_advances
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
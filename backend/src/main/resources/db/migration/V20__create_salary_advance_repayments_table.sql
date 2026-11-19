-- HRMS Pro Migration V20: Salary Advance Repayments

-- Table to store repayment installments for salary advances deducted in payroll runs
CREATE TABLE IF NOT EXISTS salary_advance_repayments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    advance_id UUID NOT NULL,
    payroll_run_id UUID NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    installment_number INT,
    deduction_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_salary_advance_repayment_advance FOREIGN KEY (tenant_id, advance_id)
        REFERENCES salary_advances (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_salary_advance_repayment_run FOREIGN KEY (tenant_id, payroll_run_id)
        REFERENCES payroll_runs (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uk_advance_run UNIQUE (advance_id, payroll_run_id)
);

-- Indexes for fast look‑ups
CREATE INDEX IF NOT EXISTS idx_salary_advance_repayments_tenant ON salary_advance_repayments (tenant_id);
CREATE INDEX IF NOT EXISTS idx_salary_advance_repayments_advance ON salary_advance_repayments (advance_id);
CREATE INDEX IF NOT EXISTS idx_salary_advance_repayments_run ON salary_advance_repayments (payroll_run_id);
ALTER TABLE salary_advance_repayments ENABLE ROW LEVEL SECURITY;
ALTER TABLE salary_advance_repayments FORCE ROW LEVEL SECURITY;

-- Row‑level security policy (consistent with other tables)
CREATE POLICY rls_salary_advance_repayments_tenant ON salary_advance_repayments
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

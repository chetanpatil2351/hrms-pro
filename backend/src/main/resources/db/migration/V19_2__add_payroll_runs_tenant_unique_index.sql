-- HRMS Pro Migration V19_2: Add composite unique index to payroll_runs

-- Ensure payroll_runs has a composite unique key on (tenant_id, id)
-- (mirrors the V15 composite‑index convention)
CREATE UNIQUE INDEX IF NOT EXISTS idx_payroll_runs_tenant_id ON payroll_runs (tenant_id, id);
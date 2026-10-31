-- ====================================================================
-- HRMS Pro Migration V8: Payroll & Salary Structure Engine
-- ====================================================================
-- Tables introduced:
--   1. salary_components: Base earning and deduction components
--   2. salary_structures: Templates grouping components
--   3. salary_structure_components: Component mappings with calculation rules
--   4. employee_salary_structures: Employee assignments with versioning & effective dates
--   5. salary_revision_history: Immutable audit of salary updates
--   6. statutory_rules: Configurable statutory rates and thresholds (EPF, ESI, PT, TDS)
--   7. payroll_periods: Monthly payroll cycle periods with lifecycle status
--   8. payroll_runs: Individual calculation runs (REGULAR, SUPPLEMENTARY)
--   9. payroll_run_employees: Employee summary outcomes in a run
--  10. payroll_line_items: Itemized earnings and deductions per employee
--  11. payroll_calculation_snapshots: Reproducible JSON snapshot of inputs & rules
--  12. payroll_adjustments: Pre-payroll additions/deductions (advances, reimbursements)
--  13. payslips: Stable payslip records for employee download/view
--
-- RLS: Default-deny on all tables using app.current_tenant_id (consistent with V2-V7)
-- ====================================================================

-- 1. Salary Components
CREATE TABLE IF NOT EXISTS salary_components (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,

    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL, -- BASIC, HRA, SPECIAL_ALLOWANCE, CONVEYANCE, MEDICAL, BONUS, PF_EMPLOYEE, PF_EMPLOYER, ESI_EMPLOYEE, ESI_EMPLOYER, PROFESSIONAL_TAX, TDS, etc.
    type VARCHAR(30) NOT NULL, -- EARNING, DEDUCTION, EMPLOYER_CONTRIBUTION
    calculation_type VARCHAR(20) NOT NULL DEFAULT 'FIXED_AMOUNT', -- FIXED_AMOUNT, PERCENTAGE
    calculation_base VARCHAR(50), -- e.g. 'BASIC', 'GROSS', null
    percentage_rate NUMERIC(6,4), -- e.g. 0.4000 for 40%

    is_taxable BOOLEAN NOT NULL DEFAULT TRUE,
    include_in_gross BOOLEAN NOT NULL DEFAULT TRUE,
    include_in_ctc BOOLEAN NOT NULL DEFAULT TRUE,
    is_statutory BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_salary_comp_tenant_code UNIQUE (tenant_id, code)
);

CREATE INDEX IF NOT EXISTS idx_salary_comp_tenant ON salary_components(tenant_id);

-- 2. Salary Structures (Templates)
CREATE TABLE IF NOT EXISTS salary_structures (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,

    name VARCHAR(150) NOT NULL,
    code VARCHAR(50) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_salary_struct_tenant_code UNIQUE (tenant_id, code)
);

CREATE INDEX IF NOT EXISTS idx_salary_struct_tenant ON salary_structures(tenant_id);

-- 3. Salary Structure Components
CREATE TABLE IF NOT EXISTS salary_structure_components (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    salary_structure_id UUID NOT NULL REFERENCES salary_structures(id) ON DELETE CASCADE,
    salary_component_id UUID NOT NULL REFERENCES salary_components(id) ON DELETE RESTRICT,

    default_amount NUMERIC(12,2) DEFAULT 0.00,
    percentage_rate NUMERIC(6,4),
    display_order INT NOT NULL DEFAULT 1,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_struct_comp UNIQUE (salary_structure_id, salary_component_id)
);

CREATE INDEX IF NOT EXISTS idx_struct_comp_tenant ON salary_structure_components(tenant_id);

-- 4. Employee Salary Structures (Assignment with Versioning)
CREATE TABLE IF NOT EXISTS employee_salary_structures (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    salary_structure_id UUID NOT NULL REFERENCES salary_structures(id) ON DELETE RESTRICT,

    version INT NOT NULL DEFAULT 1,
    effective_from DATE NOT NULL,
    effective_to DATE, -- NULL for open-ended active assignment

    annual_ctc NUMERIC(14,2) NOT NULL,
    monthly_gross NUMERIC(14,2) NOT NULL,
    monthly_base_salary NUMERIC(14,2) NOT NULL,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_emp_salary_struct_tenant ON employee_salary_structures(tenant_id);
CREATE INDEX IF NOT EXISTS idx_emp_salary_struct_emp ON employee_salary_structures(employee_id);
CREATE INDEX IF NOT EXISTS idx_emp_salary_struct_dates ON employee_salary_structures(tenant_id, employee_id, effective_from);

-- 5. Salary Revision History
CREATE TABLE IF NOT EXISTS salary_revision_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    previous_structure_id UUID REFERENCES salary_structures(id) ON DELETE SET NULL,
    new_structure_id UUID NOT NULL REFERENCES salary_structures(id) ON DELETE RESTRICT,

    previous_annual_ctc NUMERIC(14,2),
    new_annual_ctc NUMERIC(14,2) NOT NULL,
    revision_date DATE NOT NULL,
    revision_reason TEXT,
    revised_by UUID REFERENCES app_users(id) ON DELETE SET NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_salary_rev_emp ON salary_revision_history(employee_id);

-- 6. Statutory Rules (Configurable EPF, ESI, PT, TDS)
CREATE TABLE IF NOT EXISTS statutory_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,

    rule_type VARCHAR(50) NOT NULL, -- EPF, ESI, PROFESSIONAL_TAX, TDS
    state VARCHAR(50), -- for state-specific taxes like Professional Tax
    effective_year INT NOT NULL,

    employee_rate NUMERIC(10,4) NOT NULL DEFAULT 0.0000,
    employer_rate NUMERIC(10,4) NOT NULL DEFAULT 0.0000,
    wage_ceiling NUMERIC(12,2), -- e.g. 15000 for EPF, 21000 for ESI
    minimum_wage_threshold NUMERIC(12,2) DEFAULT 0.00,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_statutory_rule UNIQUE (tenant_id, rule_type, state, effective_year)
);

CREATE INDEX IF NOT EXISTS idx_statutory_rules_tenant ON statutory_rules(tenant_id);

-- 7. Payroll Periods
CREATE TABLE IF NOT EXISTS payroll_periods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,

    period_year INT NOT NULL,
    period_month INT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT', -- DRAFT, PROCESSING, REVIEW, APPROVED, FINALIZED, PAID

    total_employees INT NOT NULL DEFAULT 0,
    total_gross_pay NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    total_net_pay NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    total_employer_cost NUMERIC(14,2) NOT NULL DEFAULT 0.00,

    finalized_by UUID REFERENCES app_users(id) ON DELETE SET NULL,
    finalized_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_payroll_period UNIQUE (tenant_id, period_year, period_month)
);

CREATE INDEX IF NOT EXISTS idx_payroll_periods_tenant ON payroll_periods(tenant_id);

-- 8. Payroll Runs
CREATE TABLE IF NOT EXISTS payroll_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    payroll_period_id UUID NOT NULL REFERENCES payroll_periods(id) ON DELETE CASCADE,

    run_type VARCHAR(50) NOT NULL DEFAULT 'REGULAR', -- REGULAR, SUPPLEMENTARY
    run_number INT NOT NULL DEFAULT 1,
    status VARCHAR(50) NOT NULL DEFAULT 'COMPLETED', -- PROCESSING, COMPLETED, FAILED

    initiated_by UUID REFERENCES app_users(id) ON DELETE SET NULL,
    completed_at TIMESTAMPTZ,
    notes TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_payroll_run UNIQUE (tenant_id, payroll_period_id, run_type, run_number)
);

CREATE INDEX IF NOT EXISTS idx_payroll_runs_period ON payroll_runs(payroll_period_id);

-- 9. Payroll Run Employees
CREATE TABLE IF NOT EXISTS payroll_run_employees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    payroll_run_id UUID NOT NULL REFERENCES payroll_runs(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE RESTRICT,

    total_days INT NOT NULL,
    payable_days NUMERIC(5,2) NOT NULL,
    unpaid_leave_days NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    worked_days NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    holidays_and_weekly_offs NUMERIC(5,2) NOT NULL DEFAULT 0.00,

    gross_earnings NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    total_deductions NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    employer_contributions NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    net_salary NUMERIC(14,2) NOT NULL DEFAULT 0.00,

    is_frozen BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_run_emp UNIQUE (payroll_run_id, employee_id)
);

CREATE INDEX IF NOT EXISTS idx_run_emp_tenant ON payroll_run_employees(tenant_id);
CREATE INDEX IF NOT EXISTS idx_run_emp_employee ON payroll_run_employees(employee_id);

-- 10. Payroll Line Items
CREATE TABLE IF NOT EXISTS payroll_line_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    payroll_run_employee_id UUID NOT NULL REFERENCES payroll_run_employees(id) ON DELETE CASCADE,
    salary_component_id UUID REFERENCES salary_components(id) ON DELETE SET NULL,

    component_name VARCHAR(100) NOT NULL,
    component_code VARCHAR(50) NOT NULL,
    type VARCHAR(30) NOT NULL, -- EARNING, DEDUCTION, EMPLOYER_CONTRIBUTION
    amount NUMERIC(12,2) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_line_items_emp ON payroll_line_items(payroll_run_employee_id);

-- 11. Payroll Calculation Snapshots (Audit & Reproducibility)
CREATE TABLE IF NOT EXISTS payroll_calculation_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    payroll_run_employee_id UUID NOT NULL REFERENCES payroll_run_employees(id) ON DELETE CASCADE,

    snapshot_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_calc_snapshots_emp ON payroll_calculation_snapshots(payroll_run_employee_id);

-- 12. Payroll Adjustments (Advances, Loans, Reimbursements)
CREATE TABLE IF NOT EXISTS payroll_adjustments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    payroll_period_id UUID REFERENCES payroll_periods(id) ON DELETE SET NULL,

    adjustment_type VARCHAR(50) NOT NULL, -- ADVANCE_DEDUCTION, LOAN_EMI, REIMBURSEMENT, BONUS, ARREARS
    amount NUMERIC(12,2) NOT NULL, -- positive = credit/earning, negative = deduction
    reason TEXT NOT NULL,
    is_processed BOOLEAN NOT NULL DEFAULT FALSE,
    idempotency_key VARCHAR(100),

    authorized_by UUID REFERENCES app_users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_payroll_adj_idemp UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_payroll_adj_emp ON payroll_adjustments(employee_id);

-- 13. Payslips
CREATE TABLE IF NOT EXISTS payslips (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    payroll_run_employee_id UUID NOT NULL REFERENCES payroll_run_employees(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    payroll_period_id UUID NOT NULL REFERENCES payroll_periods(id) ON DELETE CASCADE,

    payslip_number VARCHAR(100) NOT NULL,
    issue_date DATE NOT NULL,
    gross_salary NUMERIC(14,2) NOT NULL,
    total_deductions NUMERIC(14,2) NOT NULL,
    net_salary NUMERIC(14,2) NOT NULL,
    payment_status VARCHAR(50) NOT NULL DEFAULT 'UNPAID', -- UNPAID, PAID, HOLD

    payslip_data_json TEXT NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_payslip_num UNIQUE (tenant_id, payslip_number)
);

CREATE INDEX IF NOT EXISTS idx_payslips_emp ON payslips(tenant_id, employee_id);
CREATE INDEX IF NOT EXISTS idx_payslips_period ON payslips(payroll_period_id);

-- =====================================================================
-- SECTION 14: Row Level Security for All Phase 1I Tables
-- =====================================================================
ALTER TABLE salary_components ENABLE ROW LEVEL SECURITY;
ALTER TABLE salary_components FORCE ROW LEVEL SECURITY;

ALTER TABLE salary_structures ENABLE ROW LEVEL SECURITY;
ALTER TABLE salary_structures FORCE ROW LEVEL SECURITY;

ALTER TABLE salary_structure_components ENABLE ROW LEVEL SECURITY;
ALTER TABLE salary_structure_components FORCE ROW LEVEL SECURITY;

ALTER TABLE employee_salary_structures ENABLE ROW LEVEL SECURITY;
ALTER TABLE employee_salary_structures FORCE ROW LEVEL SECURITY;

ALTER TABLE salary_revision_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE salary_revision_history FORCE ROW LEVEL SECURITY;

ALTER TABLE statutory_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE statutory_rules FORCE ROW LEVEL SECURITY;

ALTER TABLE payroll_periods ENABLE ROW LEVEL SECURITY;
ALTER TABLE payroll_periods FORCE ROW LEVEL SECURITY;

ALTER TABLE payroll_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE payroll_runs FORCE ROW LEVEL SECURITY;

ALTER TABLE payroll_run_employees ENABLE ROW LEVEL SECURITY;
ALTER TABLE payroll_run_employees FORCE ROW LEVEL SECURITY;

ALTER TABLE payroll_line_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE payroll_line_items FORCE ROW LEVEL SECURITY;

ALTER TABLE payroll_calculation_snapshots ENABLE ROW LEVEL SECURITY;
ALTER TABLE payroll_calculation_snapshots FORCE ROW LEVEL SECURITY;

ALTER TABLE payroll_adjustments ENABLE ROW LEVEL SECURITY;
ALTER TABLE payroll_adjustments FORCE ROW LEVEL SECURITY;

ALTER TABLE payslips ENABLE ROW LEVEL SECURITY;
ALTER TABLE payslips FORCE ROW LEVEL SECURITY;

-- Policies
CREATE POLICY rls_salary_components_tenant ON salary_components
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

CREATE POLICY rls_salary_structures_tenant ON salary_structures
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

CREATE POLICY rls_salary_structure_components_tenant ON salary_structure_components
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

CREATE POLICY rls_employee_salary_structures_tenant ON employee_salary_structures
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

CREATE POLICY rls_salary_revision_history_tenant ON salary_revision_history
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

CREATE POLICY rls_statutory_rules_tenant ON statutory_rules
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

CREATE POLICY rls_payroll_periods_tenant ON payroll_periods
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

CREATE POLICY rls_payroll_runs_tenant ON payroll_runs
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

CREATE POLICY rls_payroll_run_employees_tenant ON payroll_run_employees
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

CREATE POLICY rls_payroll_line_items_tenant ON payroll_line_items
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

CREATE POLICY rls_payroll_calculation_snapshots_tenant ON payroll_calculation_snapshots
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

CREATE POLICY rls_payroll_adjustments_tenant ON payroll_adjustments
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

CREATE POLICY rls_payslips_tenant ON payslips
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

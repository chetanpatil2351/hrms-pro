-- ====================================================================
-- HRMS Pro Migration V7: Leave Management & Holiday Calendar Engine
-- ====================================================================
-- Tables introduced:
--   1. holidays: Company-wide and branch-specific holidays
--   2. leave_types: Configurable leave types (CL, SL, EL, unpaid, comp-off, etc.)
--   3. leave_policies: Detailed policy rules (accrual, carry forward, min/max limits, etc.)
--   4. employee_leave_balances: Live balance tracking per employee per leave type per cycle/year
--   5. leave_ledger: Immutable audit ledger for all balance credits, debits, accruals, reversals
--   6. leave_applications: Employee leave requests (full-day, half-day, multi-day)
--   7. leave_approval_steps: Multi-level or manager approval trail per application
--
-- RLS: Default-deny on all tables using app.current_tenant_id (consistent with V2-V6)
-- Additive change to attendance_records: leave_application_id FK
-- ====================================================================

-- =====================================================================
-- SECTION 1: Holiday Calendar
-- =====================================================================
CREATE TABLE IF NOT EXISTS holidays (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE CASCADE, -- NULL = company-wide holiday

    name VARCHAR(255) NOT NULL,
    holiday_date DATE NOT NULL,
    description TEXT,
    holiday_type VARCHAR(50) NOT NULL DEFAULT 'PUBLIC', -- PUBLIC, RESTRICTED, OPTIONAL, COMPANY
    is_recurring BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_holiday_tenant_branch_date UNIQUE (tenant_id, branch_id, holiday_date)
);

CREATE INDEX IF NOT EXISTS idx_holidays_tenant ON holidays(tenant_id);
CREATE INDEX IF NOT EXISTS idx_holidays_branch ON holidays(branch_id);
CREATE INDEX IF NOT EXISTS idx_holidays_date ON holidays(tenant_id, holiday_date);

-- =====================================================================
-- SECTION 2: Leave Types
-- =====================================================================
CREATE TABLE IF NOT EXISTS leave_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,

    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL, -- CASUAL_LEAVE, SICK_LEAVE, EARNED_LEAVE, UNPAID_LEAVE, COMPENSATORY_OFF, MATERNITY_LEAVE, PATERNITY_LEAVE, etc.
    description TEXT,
    is_paid BOOLEAN NOT NULL DEFAULT TRUE,
    category VARCHAR(50) NOT NULL DEFAULT 'ANNUAL', -- ANNUAL, MEDICAL, SPECIAL, UNPAID, COMP_OFF

    is_half_day_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    requires_attachment BOOLEAN NOT NULL DEFAULT FALSE,
    attachment_mandatory_days INT NOT NULL DEFAULT 3, -- e.g. sick leave > 3 days requires medical certificate
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_leave_type_tenant_code UNIQUE (tenant_id, code)
);

CREATE INDEX IF NOT EXISTS idx_leave_types_tenant ON leave_types(tenant_id);

-- =====================================================================
-- SECTION 3: Leave Policies (Configurable rules)
-- =====================================================================
CREATE TABLE IF NOT EXISTS leave_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    leave_type_id UUID NOT NULL REFERENCES leave_types(id) ON DELETE CASCADE,

    name VARCHAR(255) NOT NULL,
    allocation_days NUMERIC(5,2) NOT NULL DEFAULT 0.00, -- e.g. 12.00 days per year
    accrual_frequency VARCHAR(50) NOT NULL DEFAULT 'MONTHLY', -- NONE, MONTHLY, QUARTERLY, ANNUAL, ON_JOINING
    accrual_rate NUMERIC(5,2) NOT NULL DEFAULT 1.00, -- e.g. 1.00 per month

    max_consecutive_days INT,
    min_notice_days INT NOT NULL DEFAULT 0,

    -- Carry-forward and Expiry
    allow_carry_forward BOOLEAN NOT NULL DEFAULT FALSE,
    max_carry_forward_days NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    carry_forward_expiry_months INT, -- e.g. expires after 3 months in new cycle

    allow_negative_balance BOOLEAN NOT NULL DEFAULT FALSE,
    max_negative_balance NUMERIC(5,2) NOT NULL DEFAULT 0.00,

    -- Encashment / Sandwich rules
    count_weekly_off_as_leave BOOLEAN NOT NULL DEFAULT FALSE,
    count_holiday_as_leave BOOLEAN NOT NULL DEFAULT FALSE,

    policy_effective_year INT NOT NULL, -- e.g. 2026
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_leave_policy_tenant_type_year UNIQUE (tenant_id, leave_type_id, policy_effective_year)
);

CREATE INDEX IF NOT EXISTS idx_leave_policies_tenant ON leave_policies(tenant_id);
CREATE INDEX IF NOT EXISTS idx_leave_policies_type ON leave_policies(leave_type_id);

-- =====================================================================
-- SECTION 4: Employee Leave Balances
-- =====================================================================
CREATE TABLE IF NOT EXISTS employee_leave_balances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    leave_type_id UUID NOT NULL REFERENCES leave_types(id) ON DELETE CASCADE,
    cycle_year INT NOT NULL, -- e.g. 2026

    opening_balance NUMERIC(6,2) NOT NULL DEFAULT 0.00,
    accrued_amount NUMERIC(6,2) NOT NULL DEFAULT 0.00,
    credited_amount NUMERIC(6,2) NOT NULL DEFAULT 0.00,
    used_amount NUMERIC(6,2) NOT NULL DEFAULT 0.00,
    pending_approval_amount NUMERIC(6,2) NOT NULL DEFAULT 0.00,
    expired_amount NUMERIC(6,2) NOT NULL DEFAULT 0.00,
    carried_forward_amount NUMERIC(6,2) NOT NULL DEFAULT 0.00,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_emp_leave_bal UNIQUE (tenant_id, employee_id, leave_type_id, cycle_year)
);

CREATE INDEX IF NOT EXISTS idx_emp_leave_bal_tenant ON employee_leave_balances(tenant_id);
CREATE INDEX IF NOT EXISTS idx_emp_leave_bal_emp ON employee_leave_balances(employee_id);

-- =====================================================================
-- SECTION 5: Leave Applications
-- =====================================================================
CREATE TABLE IF NOT EXISTS leave_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    leave_type_id UUID NOT NULL REFERENCES leave_types(id) ON DELETE RESTRICT,

    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    is_half_day BOOLEAN NOT NULL DEFAULT FALSE,
    half_day_session VARCHAR(20), -- FIRST_HALF, SECOND_HALF
    total_days NUMERIC(4,2) NOT NULL, -- e.g. 1.0, 0.5, 3.0

    reason TEXT NOT NULL,
    attachment_url TEXT,

    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, APPROVED, REJECTED, CANCELLED, WITHDRAWN
    policy_snapshot TEXT, -- JSON snapshot of the leave policy at time of application

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_leave_app_tenant ON leave_applications(tenant_id);
CREATE INDEX IF NOT EXISTS idx_leave_app_emp ON leave_applications(employee_id);
CREATE INDEX IF NOT EXISTS idx_leave_app_dates ON leave_applications(tenant_id, start_date, end_date);
CREATE INDEX IF NOT EXISTS idx_leave_app_status ON leave_applications(tenant_id, status);

-- =====================================================================
-- SECTION 6: Leave Approval Steps (Audit Trail)
-- =====================================================================
CREATE TABLE IF NOT EXISTS leave_approval_steps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    leave_application_id UUID NOT NULL REFERENCES leave_applications(id) ON DELETE CASCADE,

    step_number INT NOT NULL DEFAULT 1,
    approver_user_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, APPROVED, REJECTED
    comments TEXT,
    action_time TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_leave_approval_app ON leave_approval_steps(leave_application_id);

-- =====================================================================
-- SECTION 7: Leave Ledger (Immutable Audit Log)
-- =====================================================================
CREATE TABLE IF NOT EXISTS leave_ledger (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    leave_type_id UUID NOT NULL REFERENCES leave_types(id) ON DELETE CASCADE,
    leave_application_id UUID REFERENCES leave_applications(id) ON DELETE SET NULL,

    cycle_year INT NOT NULL,
    transaction_type VARCHAR(50) NOT NULL, -- ALLOCATION, ACCRUAL, DEBIT, CREDIT_REVERSAL, MANUAL_ADJUSTMENT, EXPIRY, CARRY_FORWARD
    amount NUMERIC(6,2) NOT NULL, -- positive for credits, negative for debits
    balance_after NUMERIC(6,2) NOT NULL,
    idempotency_key VARCHAR(100), -- prevents duplicate accrual or adjustment
    notes TEXT,
    created_by UUID REFERENCES app_users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_leave_ledger_idempotency UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_leave_ledger_tenant ON leave_ledger(tenant_id);
CREATE INDEX IF NOT EXISTS idx_leave_ledger_emp ON leave_ledger(employee_id);
CREATE INDEX IF NOT EXISTS idx_leave_ledger_type ON leave_ledger(leave_type_id);

-- =====================================================================
-- SECTION 8: Additive Changes to attendance_records
-- =====================================================================
ALTER TABLE attendance_records
    ADD COLUMN IF NOT EXISTS leave_application_id UUID REFERENCES leave_applications(id) ON DELETE SET NULL;

-- =====================================================================
-- SECTION 9: Row Level Security for All Phase 1H Tables
-- =====================================================================
ALTER TABLE holidays ENABLE ROW LEVEL SECURITY;
ALTER TABLE holidays FORCE ROW LEVEL SECURITY;

ALTER TABLE leave_types ENABLE ROW LEVEL SECURITY;
ALTER TABLE leave_types FORCE ROW LEVEL SECURITY;

ALTER TABLE leave_policies ENABLE ROW LEVEL SECURITY;
ALTER TABLE leave_policies FORCE ROW LEVEL SECURITY;

ALTER TABLE employee_leave_balances ENABLE ROW LEVEL SECURITY;
ALTER TABLE employee_leave_balances FORCE ROW LEVEL SECURITY;

ALTER TABLE leave_applications ENABLE ROW LEVEL SECURITY;
ALTER TABLE leave_applications FORCE ROW LEVEL SECURITY;

ALTER TABLE leave_approval_steps ENABLE ROW LEVEL SECURITY;
ALTER TABLE leave_approval_steps FORCE ROW LEVEL SECURITY;

ALTER TABLE leave_ledger ENABLE ROW LEVEL SECURITY;
ALTER TABLE leave_ledger FORCE ROW LEVEL SECURITY;

CREATE POLICY rls_holidays_tenant ON holidays
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

CREATE POLICY rls_leave_types_tenant ON leave_types
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

CREATE POLICY rls_leave_policies_tenant ON leave_policies
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

CREATE POLICY rls_employee_leave_balances_tenant ON employee_leave_balances
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

CREATE POLICY rls_leave_applications_tenant ON leave_applications
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

CREATE POLICY rls_leave_approval_steps_tenant ON leave_approval_steps
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

CREATE POLICY rls_leave_ledger_tenant ON leave_ledger
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

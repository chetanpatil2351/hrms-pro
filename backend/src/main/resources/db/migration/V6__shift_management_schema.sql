-- ====================================================================
-- HRMS Pro Migration V6: Shift Management and Scheduling Engine
-- ====================================================================
-- Decisions implemented:
--   1. Weekly offs: JSONB array of day names e.g. ["SATURDAY","SUNDAY"]
--   2. Rosters in DRAFT status; explicit publish/cancel operations
--   3. Multiple sessions per employee per attendance date (session_number discriminator)
--   4. Additive changes to attendance_records only; existing rows get session_number = 1 via DEFAULT
--   5. Interval-overlap detection with open-ended assignment support in service layer
--   6. Historical shift policy snapshots stored on attendance_records (never overwritten)
--   7. RLS consistent with V2-V5 using app.current_tenant_id
-- ====================================================================

-- =====================================================================
-- SECTION 1: Shift Templates (Master shift configuration)
-- =====================================================================
CREATE TABLE IF NOT EXISTS shift_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,

    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) NOT NULL,
    description TEXT,

    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    is_overnight BOOLEAN NOT NULL DEFAULT FALSE,
    timezone VARCHAR(100) NOT NULL DEFAULT 'Asia/Kolkata',
    total_scheduled_minutes INT NOT NULL,

    grace_period_minutes INT NOT NULL DEFAULT 0,
    late_arrival_policy VARCHAR(50) NOT NULL DEFAULT 'MARK_LATE',
    early_departure_policy VARCHAR(50) NOT NULL DEFAULT 'MARK_EARLY',

    break_policy VARCHAR(50) NOT NULL DEFAULT 'FLEXIBLE',
    break_allowed_minutes INT NOT NULL DEFAULT 0,

    weekly_off_days TEXT NOT NULL DEFAULT '[]',

    effective_from DATE NOT NULL DEFAULT CURRENT_DATE,
    effective_to DATE,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_tenant_shift_code UNIQUE (tenant_id, code)
);

CREATE INDEX IF NOT EXISTS idx_shift_templates_tenant ON shift_templates(tenant_id);
CREATE INDEX IF NOT EXISTS idx_shift_templates_branch ON shift_templates(branch_id);
CREATE INDEX IF NOT EXISTS idx_shift_templates_active ON shift_templates(tenant_id, is_active);

-- =====================================================================
-- SECTION 2: Employee Shift Assignments
-- =====================================================================
CREATE TABLE IF NOT EXISTS employee_shift_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    shift_template_id UUID NOT NULL REFERENCES shift_templates(id) ON DELETE RESTRICT,

    assignment_type VARCHAR(50) NOT NULL DEFAULT 'PERMANENT',
    effective_start DATE NOT NULL,
    effective_end DATE,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',

    rotation_cycle_days INT,
    rotation_sequence INT,

    assigned_by UUID REFERENCES app_users(id) ON DELETE SET NULL,
    notes TEXT,
    cancellation_reason TEXT,
    cancelled_at TIMESTAMPTZ,
    cancelled_by UUID REFERENCES app_users(id) ON DELETE SET NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_shift_assignments_tenant ON employee_shift_assignments(tenant_id);
CREATE INDEX IF NOT EXISTS idx_shift_assignments_employee ON employee_shift_assignments(employee_id);
CREATE INDEX IF NOT EXISTS idx_shift_assignments_shift ON employee_shift_assignments(shift_template_id);
CREATE INDEX IF NOT EXISTS idx_shift_assignments_dates ON employee_shift_assignments(employee_id, effective_start, effective_end);
CREATE INDEX IF NOT EXISTS idx_shift_assignments_status ON employee_shift_assignments(tenant_id, status);

-- =====================================================================
-- SECTION 3: Shift Rosters (Daily schedule records)
-- =====================================================================
CREATE TABLE IF NOT EXISTS shift_rosters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    shift_assignment_id UUID REFERENCES employee_shift_assignments(id) ON DELETE SET NULL,
    shift_template_id UUID REFERENCES shift_templates(id) ON DELETE SET NULL,

    roster_date DATE NOT NULL,
    is_weekly_off BOOLEAN NOT NULL DEFAULT FALSE,
    is_holiday BOOLEAN NOT NULL DEFAULT FALSE,

    scheduled_start_utc TIMESTAMPTZ,
    scheduled_end_utc TIMESTAMPTZ,
    scheduled_minutes INT,
    shift_snapshot TEXT,

    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    generated_by UUID REFERENCES app_users(id) ON DELETE SET NULL,
    published_by UUID REFERENCES app_users(id) ON DELETE SET NULL,
    published_at TIMESTAMPTZ,
    cancelled_by UUID REFERENCES app_users(id) ON DELETE SET NULL,
    cancelled_at TIMESTAMPTZ,

    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_roster_employee_date UNIQUE (tenant_id, employee_id, roster_date)
);

CREATE INDEX IF NOT EXISTS idx_shift_rosters_tenant ON shift_rosters(tenant_id);
CREATE INDEX IF NOT EXISTS idx_shift_rosters_employee ON shift_rosters(employee_id);
CREATE INDEX IF NOT EXISTS idx_shift_rosters_date ON shift_rosters(tenant_id, roster_date);
CREATE INDEX IF NOT EXISTS idx_shift_rosters_status ON shift_rosters(tenant_id, status);

-- =====================================================================
-- SECTION 4: Shift Change Requests
-- =====================================================================
CREATE TABLE IF NOT EXISTS shift_change_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    requested_by UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,

    current_assignment_id UUID REFERENCES employee_shift_assignments(id) ON DELETE SET NULL,
    requested_shift_id UUID NOT NULL REFERENCES shift_templates(id) ON DELETE CASCADE,
    requested_effective_date DATE NOT NULL,
    requested_effective_end DATE,

    reason TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',

    reviewed_by UUID REFERENCES app_users(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMPTZ,
    reviewer_remarks TEXT,

    applied_assignment_id UUID REFERENCES employee_shift_assignments(id) ON DELETE SET NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_shift_change_req_tenant ON shift_change_requests(tenant_id);
CREATE INDEX IF NOT EXISTS idx_shift_change_req_employee ON shift_change_requests(employee_id);
CREATE INDEX IF NOT EXISTS idx_shift_change_req_status ON shift_change_requests(tenant_id, status);

-- =====================================================================
-- SECTION 5: Shift Assignment History (Immutable audit log)
-- =====================================================================
CREATE TABLE IF NOT EXISTS shift_assignment_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    assignment_id UUID REFERENCES employee_shift_assignments(id) ON DELETE SET NULL,
    old_shift_id UUID REFERENCES shift_templates(id) ON DELETE SET NULL,
    new_shift_id UUID REFERENCES shift_templates(id) ON DELETE SET NULL,
    action VARCHAR(50) NOT NULL,
    change_reason TEXT,
    performed_by UUID REFERENCES app_users(id) ON DELETE SET NULL,
    effective_start DATE,
    effective_end DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_shift_history_tenant ON shift_assignment_history(tenant_id);
CREATE INDEX IF NOT EXISTS idx_shift_history_employee ON shift_assignment_history(employee_id);
CREATE INDEX IF NOT EXISTS idx_shift_history_assignment ON shift_assignment_history(assignment_id);

-- =====================================================================
-- SECTION 6: Additive Changes to attendance_records
-- =====================================================================

-- Add session_number for split-shift / multiple daily sessions
ALTER TABLE attendance_records
    ADD COLUMN IF NOT EXISTS session_number INT NOT NULL DEFAULT 1;

-- Add shift linkage (nullable; historical records have no shift)
ALTER TABLE attendance_records
    ADD COLUMN IF NOT EXISTS assigned_shift_id UUID REFERENCES employee_shift_assignments(id) ON DELETE SET NULL;

-- Shift policy snapshot (immutable after check-in; never overwritten on template change)
ALTER TABLE attendance_records
    ADD COLUMN IF NOT EXISTS shift_snapshot TEXT;

-- Pre-computed scheduled times at check-in (snapshot for historical accuracy)
ALTER TABLE attendance_records
    ADD COLUMN IF NOT EXISTS scheduled_start_utc TIMESTAMPTZ;
ALTER TABLE attendance_records
    ADD COLUMN IF NOT EXISTS scheduled_end_utc TIMESTAMPTZ;
ALTER TABLE attendance_records
    ADD COLUMN IF NOT EXISTS scheduled_work_minutes INT;

-- Late and early departure detail (minutes relative to schedule + grace)
ALTER TABLE attendance_records
    ADD COLUMN IF NOT EXISTS late_by_minutes INT DEFAULT 0;
ALTER TABLE attendance_records
    ADD COLUMN IF NOT EXISTS early_departure_by_minutes INT DEFAULT 0;

-- Add unique constraint for multi-session isolation
-- DROP first for idempotent re-runs in test environments
ALTER TABLE attendance_records
    DROP CONSTRAINT IF EXISTS uk_attendance_session;

ALTER TABLE attendance_records
    ADD CONSTRAINT uk_attendance_session
    UNIQUE (tenant_id, employee_id, attendance_date, session_number);

CREATE INDEX IF NOT EXISTS idx_att_rec_session
    ON attendance_records(employee_id, attendance_date, session_number);

-- =====================================================================
-- SECTION 7: Row Level Security for All New Tables
-- =====================================================================
ALTER TABLE shift_templates ENABLE ROW LEVEL SECURITY;
ALTER TABLE shift_templates FORCE ROW LEVEL SECURITY;

ALTER TABLE employee_shift_assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE employee_shift_assignments FORCE ROW LEVEL SECURITY;

ALTER TABLE shift_rosters ENABLE ROW LEVEL SECURITY;
ALTER TABLE shift_rosters FORCE ROW LEVEL SECURITY;

ALTER TABLE shift_change_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE shift_change_requests FORCE ROW LEVEL SECURITY;

ALTER TABLE shift_assignment_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE shift_assignment_history FORCE ROW LEVEL SECURITY;

CREATE POLICY rls_shift_templates_tenant ON shift_templates
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

CREATE POLICY rls_employee_shift_assignments_tenant ON employee_shift_assignments
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

CREATE POLICY rls_shift_rosters_tenant ON shift_rosters
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

CREATE POLICY rls_shift_change_requests_tenant ON shift_change_requests
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

CREATE POLICY rls_shift_assignment_history_tenant ON shift_assignment_history
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

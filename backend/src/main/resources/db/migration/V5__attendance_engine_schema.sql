-- ====================================================================
-- HRMS Pro Migration V5: Attendance & Geofencing Engine Schema
-- ====================================================================

-- 1. Attendance Geofence Locations
CREATE TABLE IF NOT EXISTS attendance_locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    branch_id UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    radius_meters INT NOT NULL DEFAULT 100, -- default 100m geofence radius
    policy_mode VARCHAR(50) NOT NULL DEFAULT 'STRICT', -- STRICT, WARNING_ONLY, APPROVAL_REQUIRED
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_att_loc_tenant ON attendance_locations(tenant_id);
CREATE INDEX IF NOT EXISTS idx_att_loc_branch ON attendance_locations(branch_id);

-- 2. Master Attendance Records
CREATE TABLE IF NOT EXISTS attendance_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    attendance_date DATE NOT NULL,
    
    check_in_time TIMESTAMPTZ NOT NULL,
    check_out_time TIMESTAMPTZ,
    
    status VARCHAR(50) NOT NULL DEFAULT 'IN_PROGRESS', -- IN_PROGRESS, PRESENT, HALF_DAY, OVERTIME, INCOMPLETE, ON_LEAVE
    work_duration_minutes INT DEFAULT 0,
    break_duration_minutes INT DEFAULT 0,
    net_work_duration_minutes INT DEFAULT 0,
    
    is_late BOOLEAN NOT NULL DEFAULT FALSE,
    is_early_departure BOOLEAN NOT NULL DEFAULT FALSE,
    is_geofence_verified BOOLEAN NOT NULL DEFAULT TRUE,
    is_selfie_verified BOOLEAN NOT NULL DEFAULT FALSE,
    
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_att_rec_tenant ON attendance_records(tenant_id);
CREATE INDEX IF NOT EXISTS idx_att_rec_emp_date ON attendance_records(employee_id, attendance_date);
CREATE INDEX IF NOT EXISTS idx_att_rec_status ON attendance_records(tenant_id, status);

-- 3. Raw Immutable Attendance Events (Audit trail for check-in / check-out)
CREATE TABLE IF NOT EXISTS attendance_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    attendance_record_id UUID NOT NULL REFERENCES attendance_records(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL, -- CHECK_IN, CHECK_OUT
    idempotency_key VARCHAR(255) UNIQUE,
    event_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Geofence data
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    accuracy_meters DOUBLE PRECISION,
    distance_meters DOUBLE PRECISION,
    geofence_location_id UUID REFERENCES attendance_locations(id) ON DELETE SET NULL,
    geofence_status VARCHAR(50) DEFAULT 'NOT_APPLICABLE', -- VERIFIED, OUT_OF_BOUNDS, WARNING, EXCEPTION
    is_mock_location BOOLEAN DEFAULT FALSE,
    
    -- Selfie metadata
    selfie_file_path VARCHAR(500),
    selfie_mime_type VARCHAR(100),
    selfie_verified BOOLEAN DEFAULT FALSE,
    
    device_info TEXT,
    ip_address VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_att_events_tenant ON attendance_events(tenant_id);
CREATE INDEX IF NOT EXISTS idx_att_events_record ON attendance_events(attendance_record_id);

-- 4. Attendance Breaks Table
CREATE TABLE IF NOT EXISTS attendance_breaks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    attendance_record_id UUID NOT NULL REFERENCES attendance_records(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    break_type VARCHAR(50) NOT NULL DEFAULT 'GENERAL', -- LUNCH, TEA, BIO, GENERAL
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ,
    duration_minutes INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_att_breaks_record ON attendance_breaks(attendance_record_id);

-- 5. Attendance Corrections & Approvals Table
CREATE TABLE IF NOT EXISTS attendance_corrections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    attendance_record_id UUID NOT NULL REFERENCES attendance_records(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    requested_check_in TIMESTAMPTZ,
    requested_check_out TIMESTAMPTZ,
    reason TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, APPROVED, REJECTED
    reviewed_by UUID REFERENCES app_users(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMPTZ,
    review_remarks TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_att_corrections_tenant ON attendance_corrections(tenant_id);

-- 6. PostgreSQL Row Level Security (RLS) with Default Deny
ALTER TABLE attendance_locations ENABLE ROW LEVEL SECURITY;
ALTER TABLE attendance_locations FORCE ROW LEVEL SECURITY;

ALTER TABLE attendance_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE attendance_records FORCE ROW LEVEL SECURITY;

ALTER TABLE attendance_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE attendance_events FORCE ROW LEVEL SECURITY;

ALTER TABLE attendance_breaks ENABLE ROW LEVEL SECURITY;
ALTER TABLE attendance_breaks FORCE ROW LEVEL SECURITY;

ALTER TABLE attendance_corrections ENABLE ROW LEVEL SECURITY;
ALTER TABLE attendance_corrections FORCE ROW LEVEL SECURITY;

-- Dynamic RLS Policies
CREATE POLICY rls_attendance_locations_tenant ON attendance_locations
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

CREATE POLICY rls_attendance_records_tenant ON attendance_records
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

CREATE POLICY rls_attendance_events_tenant ON attendance_events
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

CREATE POLICY rls_attendance_breaks_tenant ON attendance_breaks
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

CREATE POLICY rls_attendance_corrections_tenant ON attendance_corrections
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
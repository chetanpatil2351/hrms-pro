-- ====================================================================
-- HRMS Pro Migration V13: Attendance Anti-Fraud & Punch Integrity Engine
-- ====================================================================

CREATE TABLE IF NOT EXISTS attendance_fraud_flags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    attendance_record_id UUID REFERENCES attendance_records(id) ON DELETE SET NULL,
    attendance_event_id UUID REFERENCES attendance_events(id) ON DELETE SET NULL,
    
    flag_type VARCHAR(100) NOT NULL, -- RAPID_PUNCH, SUSPICIOUS_JUMP, IMPOSSIBLE_TRAVEL, REPEATED_GEOFENCE_FAILURE, REPEATED_LOW_ACCURACY, MOCK_LOCATION_REPORTED
    severity VARCHAR(50) NOT NULL DEFAULT 'MEDIUM', -- LOW, MEDIUM, HIGH, CRITICAL
    reason TEXT NOT NULL,
    details TEXT,
    
    status VARCHAR(50) NOT NULL DEFAULT 'FLAGGED', -- FLAGGED, UNDER_REVIEW, RESOLVED, DISMISSED
    reviewed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMPTZ,
    review_remarks TEXT,
    
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fraud_flags_tenant ON attendance_fraud_flags(tenant_id);
CREATE INDEX IF NOT EXISTS idx_fraud_flags_employee ON attendance_fraud_flags(tenant_id, employee_id);
CREATE INDEX IF NOT EXISTS idx_fraud_flags_status ON attendance_fraud_flags(tenant_id, status);

-- Enable RLS
ALTER TABLE attendance_fraud_flags ENABLE ROW LEVEL SECURITY;
ALTER TABLE attendance_fraud_flags FORCE ROW LEVEL SECURITY;

CREATE POLICY rls_attendance_fraud_flags_tenant ON attendance_fraud_flags
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    );

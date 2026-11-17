-- ====================================================================
-- HRMS Pro Migration V15: Tenant‑Safe Composite FK Constraints for Fraud Flags
-- ====================================================================

-- 1. Verify data consistency before adding constraints
DO $$
DECLARE cnt INT;
BEGIN
    -- employee tenant match
    SELECT COUNT(*) INTO cnt
    FROM attendance_fraud_flags f
    LEFT JOIN employees e ON f.employee_id = e.id
    WHERE f.tenant_id <> e.tenant_id;
    IF cnt > 0 THEN
        RAISE EXCEPTION 'Tenant mismatch: % fraud flags reference employees from other tenants', cnt;
    END IF;

    -- attendance_record tenant match (nullable)
    SELECT COUNT(*) INTO cnt
    FROM attendance_fraud_flags f
    LEFT JOIN attendance_records r ON f.attendance_record_id = r.id
    WHERE f.attendance_record_id IS NOT NULL
      AND f.tenant_id <> r.tenant_id;
    IF cnt > 0 THEN
        RAISE EXCEPTION 'Tenant mismatch: % fraud flags reference attendance_records from other tenants', cnt;
    END IF;

    -- attendance_event tenant match (nullable)
    SELECT COUNT(*) INTO cnt
    FROM attendance_fraud_flags f
    LEFT JOIN attendance_events ev ON f.attendance_event_id = ev.id
    WHERE f.attendance_event_id IS NOT NULL
      AND f.tenant_id <> ev.tenant_id;
    IF cnt > 0 THEN
        RAISE EXCEPTION 'Tenant mismatch: % fraud flags reference attendance_events from other tenants', cnt;
    END IF;
END $$;

-- 2. Ensure composite unique indexes exist (tenant_id, id)
CREATE UNIQUE INDEX IF NOT EXISTS uq_employees_tenant_id ON employees (tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_attendance_records_tenant_id ON attendance_records (tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_attendance_events_tenant_id ON attendance_events (tenant_id, id);

-- 3. Add composite foreign‑key constraints (tenant_id + id) without ON DELETE actions
ALTER TABLE attendance_fraud_flags
    ADD CONSTRAINT fk_fraud_flag_employee_tenant
        FOREIGN KEY (tenant_id, employee_id)
        REFERENCES employees (tenant_id, id) ON DELETE CASCADE;

ALTER TABLE attendance_fraud_flags
    ADD CONSTRAINT fk_fraud_flag_record_tenant
        FOREIGN KEY (tenant_id, attendance_record_id)
        REFERENCES attendance_records (tenant_id, id) ON DELETE SET NULL;

ALTER TABLE attendance_fraud_flags
    ADD CONSTRAINT fk_fraud_flag_event_tenant
        FOREIGN KEY (tenant_id, attendance_event_id)
        REFERENCES attendance_events (tenant_id, id) ON DELETE SET NULL;
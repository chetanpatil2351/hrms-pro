
-- ====================================================================
-- HRMS Pro Migration V17:
-- Fix composite FK delete behavior for attendance fraud flags
-- ====================================================================

-- 1. Drop existing composite foreign keys
ALTER TABLE attendance_fraud_flags
    DROP CONSTRAINT fk_fraud_flag_record_tenant;

ALTER TABLE attendance_fraud_flags
    DROP CONSTRAINT fk_fraud_flag_event_tenant;

-- 2. Recreate record FK.
-- Only attendance_record_id will become NULL on parent deletion.
ALTER TABLE attendance_fraud_flags
    ADD CONSTRAINT fk_fraud_flag_record_tenant
    FOREIGN KEY (tenant_id, attendance_record_id)
    REFERENCES attendance_records (tenant_id, id)
    ON DELETE SET NULL (attendance_record_id);

-- 3. Recreate event FK.
-- Only attendance_event_id will become NULL on parent deletion.
ALTER TABLE attendance_fraud_flags
    ADD CONSTRAINT fk_fraud_flag_event_tenant
    FOREIGN KEY (tenant_id, attendance_event_id)
    REFERENCES attendance_events (tenant_id, id)
    ON DELETE SET NULL (attendance_event_id);


-- HRMS Pro Migration V18:
-- Correct composite FK delete behavior for attendance fraud flags

ALTER TABLE attendance_fraud_flags
    DROP CONSTRAINT fk_fraud_flag_record_tenant;

ALTER TABLE attendance_fraud_flags
    DROP CONSTRAINT fk_fraud_flag_event_tenant;

ALTER TABLE attendance_fraud_flags
    ADD CONSTRAINT fk_fraud_flag_record_tenant
    FOREIGN KEY (tenant_id, attendance_record_id)
    REFERENCES attendance_records (tenant_id, id)
    ON DELETE SET NULL (attendance_record_id);

ALTER TABLE attendance_fraud_flags
    ADD CONSTRAINT fk_fraud_flag_event_tenant
    FOREIGN KEY (tenant_id, attendance_event_id)
    REFERENCES attendance_events (tenant_id, id)
    ON DELETE SET NULL (attendance_event_id);

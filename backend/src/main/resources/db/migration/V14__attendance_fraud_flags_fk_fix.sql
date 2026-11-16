-- ====================================================================
-- HRMS Pro Migration V14: Fix FK for reviewed_by to reference app_users
-- ====================================================================

-- Drop the incorrect foreign key that references the legacy 'users' table.
ALTER TABLE attendance_fraud_flags
    DROP CONSTRAINT IF EXISTS attendance_fraud_flags_reviewed_by_fkey;

-- Add the correct foreign key referencing the global app_users table.
ALTER TABLE attendance_fraud_flags
    ADD CONSTRAINT fk_attendance_fraud_flags_reviewed_by
    FOREIGN KEY (reviewed_by)
    REFERENCES app_users(id)
    ON DELETE SET NULL;

-- Add JSON column to store failed employee information per payroll run
ALTER TABLE payroll_runs
    ADD COLUMN failed_employees_json TEXT;

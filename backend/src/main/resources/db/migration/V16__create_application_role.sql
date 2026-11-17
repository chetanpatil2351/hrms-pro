
-- ====================================================================
-- HRMS Pro Migration V16: Create non-superuser application role
-- ====================================================================

-- Create the role if it does not already exist.
-- NOSUPERUSER, NOCREATEDB, NOCREATEROLE, NOREPLICATION are PostgreSQL keywords
-- that must be written without spaces (NO SUPERUSER is invalid syntax).
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_roles WHERE rolname = 'hrms_app'
    ) THEN
        CREATE ROLE hrms_app
            NOLOGIN
            NOINHERIT
            NOCREATEDB
            NOCREATEROLE
            NOREPLICATION
            NOSUPERUSER
            NOBYPASSRLS;
    END IF;
END $$;

-- Ensure correct attributes regardless of whether the role was just created
-- or already existed from a previous partial run.
-- Note: ALTER ROLE uses the same no-space keywords as CREATE ROLE.
ALTER ROLE hrms_app
    LOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    NOREPLICATION
    NOBYPASSRLS;

-- Database and schema access
GRANT CONNECT ON DATABASE hrms_db TO hrms_app;
GRANT USAGE ON SCHEMA public TO hrms_app;

-- Existing table permissions
GRANT SELECT, INSERT, UPDATE, DELETE
ON ALL TABLES IN SCHEMA public
TO hrms_app;

-- Existing sequence permissions
GRANT USAGE, SELECT
ON ALL SEQUENCES IN SCHEMA public
TO hrms_app;

-- Future tables created by the migration owner
ALTER DEFAULT PRIVILEGES IN SCHEMA public
GRANT SELECT, INSERT, UPDATE, DELETE
ON TABLES TO hrms_app;

-- Future sequences created by the migration owner
ALTER DEFAULT PRIVILEGES IN SCHEMA public
GRANT USAGE, SELECT
ON SEQUENCES TO hrms_app;

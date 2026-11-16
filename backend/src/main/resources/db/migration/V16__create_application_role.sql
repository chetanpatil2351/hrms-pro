
-- ====================================================================
-- HRMS Pro Migration V16: Create non-superuser application role
-- ====================================================================

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
            NOREPLICATION;
    END IF;
END $$;

-- Application role must be able to authenticate,
-- but must never bypass PostgreSQL RLS.
ALTER ROLE hrms_app
    LOGIN
    NO SUPERUSER
    NO CREATEDB
    NO CREATEROLE
    NO REPLICATION
    NO BYPASSRLS;

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

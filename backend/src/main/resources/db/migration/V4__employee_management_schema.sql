-- ====================================================================
-- HRMS Pro Migration V4: Employee Management & Document Metadata Schema
-- ====================================================================

-- 1. Employees Table
CREATE TABLE IF NOT EXISTS employees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id UUID REFERENCES app_users(id) ON DELETE SET NULL, -- optional link to global user
    membership_id UUID REFERENCES tenant_memberships(id) ON DELETE SET NULL, -- optional link to active membership
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    department_id UUID REFERENCES departments(id) ON DELETE SET NULL,
    reporting_manager_id UUID REFERENCES employees(id) ON DELETE SET NULL,
    
    employee_code VARCHAR(50) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    middle_name VARCHAR(100),
    last_name VARCHAR(100) NOT NULL,
    date_of_birth DATE,
    gender VARCHAR(20),
    personal_email VARCHAR(255),
    work_email VARCHAR(255),
    phone VARCHAR(50),
    address TEXT,
    profile_photo_url TEXT,
    
    joining_date DATE NOT NULL,
    employment_type VARCHAR(50) NOT NULL DEFAULT 'FULL_TIME', -- FULL_TIME, PART_TIME, CONTRACT, INTERN
    designation VARCHAR(150) NOT NULL,
    probation_end_date DATE,
    employment_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, PROBATION, ON_LEAVE, RESIGNED, TERMINATED
    
    -- Emergency Contact
    emergency_contact_name VARCHAR(150),
    emergency_contact_relation VARCHAR(50),
    emergency_contact_phone VARCHAR(50),
    
    -- Sensitive Identity Information (Masked/Encrypted reference)
    pan_number_masked VARCHAR(20),
    aadhaar_number_masked VARCHAR(20),
    
    created_by UUID REFERENCES app_users(id) ON DELETE SET NULL,
    updated_by UUID REFERENCES app_users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_tenant_employee_code UNIQUE (tenant_id, employee_code)
);

CREATE INDEX IF NOT EXISTS idx_employees_tenant ON employees(tenant_id);
CREATE INDEX IF NOT EXISTS idx_employees_user ON employees(user_id);
CREATE INDEX IF NOT EXISTS idx_employees_branch ON employees(branch_id);
CREATE INDEX IF NOT EXISTS idx_employees_dept ON employees(department_id);
CREATE INDEX IF NOT EXISTS idx_employees_manager ON employees(reporting_manager_id);
CREATE INDEX IF NOT EXISTS idx_employees_status ON employees(tenant_id, employment_status);

-- 2. Employee Documents Table (Private storage metadata)
CREATE TABLE IF NOT EXISTS employee_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    document_type VARCHAR(50) NOT NULL, -- RESUME, ID_PROOF, ADDRESS_PROOF, EDUCATION, OFFER_LETTER, CONTRACT, OTHER
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL, -- Private object storage path/key
    mime_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    uploaded_by UUID REFERENCES app_users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_emp_docs_tenant ON employee_documents(tenant_id);
CREATE INDEX IF NOT EXISTS idx_emp_docs_employee ON employee_documents(employee_id);

-- 3. Employee Audit History Table
CREATE TABLE IF NOT EXISTS employee_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    actor_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
    action VARCHAR(50) NOT NULL, -- CREATED, UPDATED, STATUS_CHANGED, BRANCH_TRANSFERRED, PROMOTED, DOCUMENT_UPLOADED
    field_name VARCHAR(100),
    old_value TEXT,
    new_value TEXT,
    details TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_emp_audit_tenant ON employee_audit_logs(tenant_id);
CREATE INDEX IF NOT EXISTS idx_emp_audit_employee ON employee_audit_logs(employee_id);

-- 4. Enable Row Level Security (RLS) with Default Deny
ALTER TABLE employees ENABLE ROW LEVEL SECURITY;
ALTER TABLE employees FORCE ROW LEVEL SECURITY;

ALTER TABLE employee_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE employee_documents FORCE ROW LEVEL SECURITY;

ALTER TABLE employee_audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE employee_audit_logs FORCE ROW LEVEL SECURITY;

-- Dynamic RLS Policies
CREATE POLICY rls_employees_tenant_isolation ON employees
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

CREATE POLICY rls_employee_documents_tenant_isolation ON employee_documents
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

CREATE POLICY rls_employee_audit_logs_tenant_isolation ON employee_audit_logs
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
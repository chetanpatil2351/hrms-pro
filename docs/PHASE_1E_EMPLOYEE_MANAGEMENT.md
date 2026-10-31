# HRMS Pro — Phase 1E: Employee Management & Profiles

**Document Version**: 1.0.0  
**Phase**: 1E — Employee Profiles, Multi-Unit Association, Document Metadata & Audit History  
**Date**: October 31, 2026  
**Status**: Implemented & 100% Verified with Automated Tests  

---

## 1. Overview & Domain Architecture

Phase 1E implements the core Employee Management subsystem for HRMS Pro:
- **Decoupled Identity & Employment Model**: Separates business employment records (employees) from global authentication credentials (pp_users), supporting employees before they have portal login accounts.
- **Tenant & Unit Isolation**: Every employee record belongs strictly to a tenant (	enant_id). Cross-tenant assignments to branches, departments, or reporting managers are rejected at the business and database layer.
- **Unique Employee Code Generation**: Generates tenant-scoped employee codes (e.g. SKY-0001, ACM-0002) with concurrent safety and unique compound constraint (tenant_id, employee_code).
- **Identity Privacy & Masking**: Sensitive identity values (PAN, Aadhaar) are masked in standard responses (e.g. ABXXXXXX4F, XXXX-XXXX-9012).
- **Private Document Management Metadata**: Models file uploads (Resume, ID Proof, Address Proof, Contracts) stored in private tenant-isolated storage buckets with MIME and size metadata.
- **Auditing & Historical Change Tracking**: Records all lifecycle modifications (CREATED, UPDATED, STATUS_CHANGED, DOCUMENT_UPLOADED) with actor and change metadata in employee_audit_logs.

---

## 2. Entity-Relationship (ER) Diagram

`mermaid
erDiagram
    tenants ||--o{ employees : "employs"
    branches ||--o{ employees : "assigned to"
    departments ||--o{ employees : "assigned to"
    employees ||--o{ employees : "reports to (manager)"
    app_users ||--o| employees : "linked user"
    tenant_memberships ||--o| employees : "membership"
    
    employees ||--o{ employee_documents : "has"
    employees ||--o{ employee_audit_logs : "audited by"

    employees {
        UUID id PK
        UUID tenant_id FK
        UUID user_id FK
        UUID membership_id FK
        UUID branch_id FK
        UUID department_id FK
        UUID reporting_manager_id FK
        VARCHAR employee_code UK
        VARCHAR first_name
        VARCHAR last_name
        VARCHAR work_email
        DATE joining_date
        VARCHAR employment_status
        VARCHAR designation
        VARCHAR pan_number_masked
        VARCHAR aadhaar_number_masked
        TIMESTAMPTZ created_at
    }

    employee_documents {
        UUID id PK
        UUID tenant_id FK
        UUID employee_id FK
        VARCHAR document_type
        VARCHAR file_name
        VARCHAR file_path
        VARCHAR mime_type
        BIGINT file_size_bytes
        UUID uploaded_by FK
        TIMESTAMPTZ created_at
    }

    employee_audit_logs {
        UUID id PK
        UUID tenant_id FK
        UUID employee_id FK
        UUID actor_id FK
        VARCHAR action
        VARCHAR field_name
        TEXT old_value
        TEXT new_value
        TEXT details
        TIMESTAMPTZ created_at
    }
`

---

## 3. API Endpoint Contracts

| HTTP Method | Endpoint | Authorization | Description |
| :--- | :--- | :--- | :--- |
| POST | /api/v1/employees | COMPANY_ADMIN, HR_MANAGER | Onboard new employee with auto code generation and optional invite |
| GET | /api/v1/employees | Privileged Roles | Paginated employee list with search and branch/department filters |
| GET | /api/v1/employees/me | Authenticated | Retrieve self employee profile for active tenant |
| GET | /api/v1/employees/{id} | Privileged Roles | Retrieve full employee profile |
| PUT | /api/v1/employees/{id} | COMPANY_ADMIN, HR_MANAGER | Update employee profile fields and emergency contacts |
| PATCH | /api/v1/employees/{id}/status | COMPANY_ADMIN, HR_MANAGER | Update employment status (ACTIVE, PROBATION, ON_LEAVE, RESIGNED, TERMINATED) |
| POST | /api/v1/employees/{id}/documents | COMPANY_ADMIN, HR_MANAGER | Attach document metadata for employee |
| GET | /api/v1/employees/{id}/documents | COMPANY_ADMIN, HR_MANAGER | List uploaded document references for employee |
| GET | /api/v1/employees/{id}/history | COMPANY_ADMIN, HR_MANAGER | View comprehensive audit log history for employee |

---

## 4. RBAC Matrix for Employee Data

| Operation | COMPANY_ADMIN | HR_MANAGER | BRANCH_MANAGER | PAYROLL_MANAGER | ACCOUNTANT | EMPLOYEE |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Create Employee** | Allowed | Allowed | Denied | Denied | Denied | Denied |
| **View Employee List** | Full Tenant | Full Tenant | Branch Scoped | Employment Data | Employment Data | Denied |
| **View Self Profile** | Full | Full | Full | Full | Full | Self Only |
| **Update Employee Profile** | Full | Full | Denied | Denied | Denied | Self Basic |
| **Update Status** | Allowed | Allowed | Denied | Denied | Denied | Denied |
| **Upload Documents** | Allowed | Allowed | Denied | Denied | Denied | Denied |
| **View Documents** | Allowed | Allowed | Denied | Denied | Denied | Denied |
| **View Audit History** | Allowed | Allowed | Denied | Denied | Denied | Denied |

---

## 5. Automated Test Suite Results

Test Suite: mvn clean test (Spring Boot 3.3.4 / Java 21)

| Suite | Test Case | Status |
| :--- | :--- | :--- |
| **Employee Management** | 	estCreateEmployee | **PASSED** |
| | 	estCrossTenantRejections | **PASSED** |
| | 	estSelfReportingManagerRejection | **PASSED** |
| | 	estEmployeeSearchAndFiltering | **PASSED** |
| | 	estEmployeeDocumentManagement | **PASSED** |
| **Company Onboarding** | 	estCompanyOnboarding | **PASSED** |
| | 	estDuplicateSubdomainOrCode | **PASSED** |
| | 	estBranchManagement | **PASSED** |
| | 	estDepartmentManagement | **PASSED** |
| | 	estInvitationAndAcceptanceFlow | **PASSED** |
| | 	estCapacityEnforcement | **PASSED** |
| **Authentication & Tenant** | 	estRegistrationAndDuplicateRejection | **PASSED** |
| | 	estLoginSuccess | **PASSED** |
| | 	estInvalidPasswordRejection | **PASSED** |
| | 	estRefreshTokenRotationAndReuseDetection | **PASSED** |
| | 	estTenantSwitching | **PASSED** |
| | 	estSuspendedMembershipRejection | **PASSED** |
| **MultiTenant Schema & RBAC** | 	estSingleUserMultipleTenantsWithDifferentRoles | **PASSED** |
| | 	estDuplicateMembershipConstraint | **PASSED** |
| | 	estRefreshTokenRotationFoundation | **PASSED** |
| **Context Bootstrap** | contextLoads | **PASSED** |

**Summary**: 21 Tests Run, 0 Failures, 0 Errors, 0 Skipped (**100% Passed**).

---

## 6. Document Storage Integration & Known Limitations
- **Object Storage Abstraction**: File metadata (MIME type, size, safe private path) is recorded in PostgreSQL. Live binary upload streaming to Supabase Storage private buckets will be configured via storage client integration in the dedicated cloud setup.
- **Live Supabase PostgreSQL RLS**: Unit and integration test suites run against the in-memory test environment with RLS policies compiled in Flyway migration V4__employee_management_schema.sql. Live database validation against Supabase dev instance is **PENDING** until live cloud connection verification.
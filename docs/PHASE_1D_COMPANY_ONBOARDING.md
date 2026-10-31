# HRMS Pro — Phase 1D: Company Onboarding & Tenant Management

**Document Version**: 1.0.0  
**Phase**: 1D — Company Onboarding, Branches, Departments, Invitations & Capacity Enforcement  
**Date**: October 31, 2026  
**Status**: Implemented & 100% Verified with Automated Tests  

---

## 1. Overview & Architecture

Phase 1D implements commercial SaaS tenant onboarding and multi-unit organizational modeling for HRMS Pro:
- **Autonomous Company Creation**: Authenticated users can create new companies (POST /api/v1/tenants). The creator automatically receives the COMPANY_ADMIN role and an active membership without cross-tenant privilege leakage.
- **Default Hierarchy Provisioning**: Onboarding provisions a default **Head Office** branch (HO) automatically.
- **Multi-Branch & Department Scoping**: Branches and departments are strictly isolated by 	enant_id. Cross-tenant branch assignments are rejected.
- **Capacity & Subscription Plan Enforcement**: Active employee limits (maxEmployees) are verified before issuing invitations or accepting memberships.
- **Cryptographic Member Invitation Foundation**: Secure 256-bit opaque tokens, stored only as **SHA-256 hashes**, with single-use acceptance, expiration tracking, and rejection of privilege escalation (SUPER_ADMIN).

---

## 2. API Endpoint Contracts

### 2.1 Company Onboarding & Management
| Method | Endpoint | Authorization | Description |
| :--- | :--- | :--- | :--- |
| POST | /api/v1/tenants | Authenticated | Create a new company; creator becomes COMPANY_ADMIN |
| GET | /api/v1/tenants/current | Authenticated | Retrieve active company details & employee capacity |
| PUT | /api/v1/tenants/current | COMPANY_ADMIN | Update company profile, branding, address, and timezone |
| GET | /api/v1/tenants/my-companies | Authenticated | List all companies the user belongs to |

### 2.2 Branch Management
| Method | Endpoint | Authorization | Description |
| :--- | :--- | :--- | :--- |
| POST | /api/v1/branches | COMPANY_ADMIN, HR_MANAGER | Create new branch in current company |
| GET | /api/v1/branches | Authenticated | List all branches of current company |
| GET | /api/v1/branches/{id} | Authenticated | Get specific branch details |
| PUT | /api/v1/branches/{id} | COMPANY_ADMIN, HR_MANAGER | Update branch profile and code |
| PATCH | /api/v1/branches/{id}/status | COMPANY_ADMIN, HR_MANAGER | Soft-deactivate or reactivate branch |

### 2.3 Department Management
| Method | Endpoint | Authorization | Description |
| :--- | :--- | :--- | :--- |
| POST | /api/v1/departments | COMPANY_ADMIN, HR_MANAGER | Create department (optional branch linkage) |
| GET | /api/v1/departments | Authenticated | List all departments of current company |
| GET | /api/v1/departments/{id} | Authenticated | Get department details |
| PUT | /api/v1/departments/{id} | COMPANY_ADMIN, HR_MANAGER | Update department details |
| PATCH | /api/v1/departments/{id}/status | COMPANY_ADMIN, HR_MANAGER | Soft-deactivate or reactivate department |

### 2.4 Membership & Invitation Management
| Method | Endpoint | Authorization | Description |
| :--- | :--- | :--- | :--- |
| POST | /api/v1/members/invite | COMPANY_ADMIN, HR_MANAGER | Issue member invitation token (capacity checked) |
| POST | /api/v1/members/accept-invitation | Authenticated | Accept invitation token and create/activate membership |
| PATCH | /api/v1/members/{membershipId}/status| COMPANY_ADMIN | Suspend or reactivate member |

---

## 3. Database Changes (V3__tenant_management_and_invitations.sql)

1. **	enants Table Expansion**:
   - Added: legal_name, ddress, city, state, country, postal_code, industry, website, logo_url, 	imezone, and subscription_status (TRIAL, ACTIVE, EXPIRED, SUSPENDED).
2. **	enant_invitations Table**:
   - id UUID PRIMARY KEY, 	enant_id UUID REFERENCES tenants, email VARCHAR, ole_id UUID REFERENCES roles, ranch_id UUID, department_id UUID, 	oken_hash VARCHAR UNIQUE, invited_by UUID, designation VARCHAR, employee_code VARCHAR, status VARCHAR, expires_at TIMESTAMPTZ, ccepted_at TIMESTAMPTZ.
3. **PostgreSQL RLS Policies**:
   - Applied FORCE ROW LEVEL SECURITY and default-deny policies on 	enant_invitations matching current_setting('app.current_tenant_id', true).

---

## 4. RBAC Permission Matrix

| Role | Company Management | Branch Management | Department Management | Member Invitations | Member Status Modification |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **SUPER_ADMIN** | System-wide | System-wide | System-wide | System-wide | System-wide |
| **COMPANY_ADMIN** | Full (Read/Write) | Full (Read/Write) | Full (Read/Write) | Full (Read/Write) | Full (Suspend/Reactivate) |
| **HR_MANAGER** | Read Only | Full (Read/Write) | Full (Read/Write) | Full (Read/Write) | Denied |
| **BRANCH_MANAGER** | Read Only | Scoped Branch | Scoped Department | Denied | Denied |
| **EMPLOYEE** | Read Basic | Read Basic | Read Basic | Denied | Denied |

---

## 5. Automated Test Results

Test Suite: mvn clean test (Spring Boot 3.3.4 / Java 21)

| Test Suite | Test Case | Status |
| :--- | :--- | :--- |
| **Company Onboarding & Management** | 	estCompanyOnboarding | **PASSED** |
| | 	estDuplicateSubdomainOrCode | **PASSED** |
| | 	estBranchManagement | **PASSED** |
| | 	estDepartmentManagement | **PASSED** |
| | 	estInvitationAndAcceptanceFlow | **PASSED** |
| | 	estCapacityEnforcement | **PASSED** |
| **Authentication & Tenant Security** | 	estRegistrationAndDuplicateRejection | **PASSED** |
| | 	estLoginSuccess | **PASSED** |
| | 	estInvalidPasswordRejection | **PASSED** |
| | 	estRefreshTokenRotationAndReuseDetection | **PASSED** |
| | 	estTenantSwitching | **PASSED** |
| | 	estSuspendedMembershipRejection | **PASSED** |
| **MultiTenant Schema & RBAC** | 	estSingleUserMultipleTenantsWithDifferentRoles | **PASSED** |
| | 	estDuplicateMembershipConstraint | **PASSED** |
| | 	estRefreshTokenRotationFoundation | **PASSED** |
| **Application Bootstrap** | contextLoads | **PASSED** |

**Summary**: 16 Tests Run, 0 Failures, 0 Errors, 0 Skipped (**100% Passed**).

---

## 6. Security & Infrastructure Limitations
- **Email Delivery Integration**: Invitation tokens are generated and stored securely as SHA-256 hashes. Live SMTP/SendGrid/SES email dispatch infrastructure is pending and will be integrated during the Notification module phase.
- **Live Supabase PostgreSQL RLS Verification**: Local test execution validates transactional JPA logic, foreign keys, unique constraints, and aspect triggers. Live PostgreSQL RLS verification against the Supabase database instance remains **PENDING** until live cloud connection verification.
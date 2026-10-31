# HRMS Pro - Project Progress & Execution Log

## Overview
- **Project**: HRMS Pro (Commercial-grade HRMS SaaS)
- **Deployment Models**: Multi-tenant Shared Cloud SaaS & Dedicated Enterprise
- **Stack**:
  - Backend: Java 21, Spring Boot 3.3.4, Maven, Spring Security, Spring Data JPA, Flyway, PostgreSQL, JWT
  - Web: React 18, Vite, Tailwind CSS (v4), React Router, Axios, Lucide React, Recharts
  - Mobile: React Native, Expo SDK 57/58, Expo Router, SecureStore, Camera, Location, SQLite
  - DB: PostgreSQL (Supabase / dedicated) with Row Level Security (RLS)

---

## Sprint Tracking & Status

### Phase 1: Inception, Environment Inspection & Monorepo Foundation
- [x] Initial repository inspection (Java 21, Maven 3.9, Node 24, Git verified)
- [x] Git repository initialization
- [x] Monorepo .gitignore setup
- [x] Documentation scaffold (README.md, CHANGELOG.md, docs/PROJECT_PROGRESS.md)
- [x] Backend skeleton initialization (ackend/ with Maven, Spring Boot 3.3.4, Java 21)
- [x] Base configuration & shared models (BaseEntity, BaseTenantEntity, ApiResponse, GlobalExceptionHandler)
- [x] Web frontend scaffold (web/ with React, Vite, Tailwind CSS, TypeScript, Axios, Lucide, Recharts - verified 
pm run build)
- [x] Mobile frontend scaffold (mobile/ with Expo SDK, React Native TypeScript, Axios, SecureStore, Location, Camera, SQLite)

### Phase 1A: Architecture & PostgreSQL Security Audit
- [x] Comprehensive database schema audit (User identity vs. Tenant decoupling)
- [x] PostgreSQL RLS policy inspection and default-deny remediation design
- [x] HikariCP session variable leakage mitigation strategy (transaction-local set_config with local scope)
- [x] Authentication & token lifecycle design (Argon2/BCrypt, short-lived JWT, family-based refresh token rotation & reuse detection)
- [x] Created docs/PHASE_1A_ARCHITECTURE_AUDIT.md

### Phase 1B: Multi-Tenant RBAC Schema Refactor
- [x] Created V2__multi_tenant_rbac_schema.sql (app_users, branches, departments, tenant_memberships, roles, permissions, membership_roles, role_permissions, refresh_tokens)
- [x] Preserved legacy data via safe PL/pgSQL data migration script
- [x] Created JPA entities and repositories with relationships and unique constraints
- [x] Created TenantContext ThreadLocal abstraction
- [x] Added automated tests in MultiTenantSchemaAndRbacTests (mvn clean test PASSED: 4/4 tests clean)
- [x] Created docs/PHASE_1B_DATABASE_IMPLEMENTATION.md

### Phase 1C: Secure Authentication, JWT & Tenant Context
- [x] Implemented JwtService (HMAC-SHA256, 15m expiration, token claims with user, tenant, membership & roles)
- [x] Implemented BCrypt password hashing (strength 12)
- [x] Implemented family-based refresh token rotation, SHA-256 token hashing, and replay attack reuse detection
- [x] Implemented Spring Security stateless filter chain (SecurityConfig, JwtAuthenticationFilter, UserPrincipal)
- [x] Implemented transaction-local PostgreSQL RLS context injector (TenantContextAspect via SELECT set_config('app.current_tenant_id', :tenantId, true))
- [x] Implemented Auth endpoints in AuthController (/register, /login, /refresh, /logout, /switch-tenant, /memberships, /me)
- [x] Added automated tests in AuthenticationAndTenantContextTests (mvn clean test PASSED: 10/10 tests clean)
- [x] Created docs/PHASE_1C_AUTHENTICATION.md

### Phase 1D: Company Onboarding & Tenant Management
- [x] Created V3__tenant_management_and_invitations.sql (expanded company fields, tenant_invitations, RLS policies)
- [x] Implemented company onboarding with automatic COMPANY_ADMIN assignment and default Head Office branch creation
- [x] Implemented Branch management APIs (POST, GET, GET by id, PUT, PATCH status)
- [x] Implemented Department management APIs (POST, GET, GET by id, PUT, PATCH status) with cross-tenant branch rejection
- [x] Implemented Member invitation foundation (cryptographic token, SHA-256 hashing, 7-day expiry, single-use acceptance)
- [x] Implemented Subscription capacity and employee limit enforcement
- [x] Added automated tests in CompanyOnboardingAndManagementTests (mvn clean test PASSED: 16/16 tests clean)
- [x] Created docs/PHASE_1D_COMPANY_ONBOARDING.md

### Phase 1E: Employee Management & Profiles
- [x] Created V4__employee_management_schema.sql (employees, employee_documents, employee_audit_logs with default-deny RLS)
- [x] Implemented Employee, EmployeeDocument, EmployeeAuditLog JPA entities and repositories
- [x] Auto employee-code generation per tenant with concurrency protection
- [x] Identity privacy: PAN and Aadhaar numbers masked in responses
- [x] Multi-unit validation: strict branch, department, and reporting manager tenant boundaries
- [x] Implemented Employee REST APIs: /employees, /employees/me, /employees/{id}, /status, /documents, /history
- [x] Verified full test suite (mvn clean test PASSED: 21/21 clean tests)
- [x] Created docs/PHASE_1E_EMPLOYEE_MANAGEMENT.md

### Phase 1F: Attendance & Geofencing Engine
- [x] Created V5__attendance_engine_schema.sql (attendance_locations, attendance_records, attendance_events, attendance_breaks, attendance_corrections with FORCE RLS)
- [x] Implemented AttendanceLocation, AttendanceRecord, AttendanceEvent, AttendanceBreak, AttendanceCorrection JPA entities & repositories
- [x] Haversine GPS geofence validation engine supporting STRICT and FLAG_OUTSIDE policy modes
- [x] Break tracking and net work duration computation with PRESENT, HALF_DAY, and INCOMPLETE classification
- [x] Attendance punch correction request, approval, and rejection workflow with audit trail
- [x] Implemented Attendance REST APIs: /check-in, /check-out, /me/today, /me/history, /breaks/start, /breaks/end, /corrections, /corrections/{id}/review
- [x] Verified full test suite (mvn clean test PASSED: 24/24 clean tests)
- [x] Created docs/PHASE_1F_ATTENDANCE_ENGINE.md

### Phase 1G: Shift Management & Scheduling Engine
- [x] Pre-implementation audit verified `app.current_tenant_id` convention across code and migrations V2-V6
- [x] Created V6__shift_management_schema.sql (shift_templates, employee_shift_assignments, shift_rosters, shift_change_requests, shift_assignment_history with FORCE RLS, additive attendance_records columns)
- [x] Implemented ShiftTemplate, EmployeeShiftAssignment, ShiftRoster, ShiftChangeRequest, ShiftAssignmentHistory JPA entities & repositories
- [x] Shift template CRUD with unique code constraint per tenant and JSON weekly off days validation
- [x] Overnight shift duration calculation and IANA timezone + DST spring-forward/fall-back arithmetic
- [x] Employee shift assignments with interval overlap detection for bounded and open-ended ranges
- [x] Bulk assignment endpoint with skipConflicting support and immutable audit history
- [x] Roster generation in DRAFT status with weekly off detection, publish, and cancellation workflows
- [x] Employee shift change requests with reason, manager review, and self-approval prevention
- [x] Split shifts and multi-session check-ins on same date using sequential session_number discriminator
- [x] Shift policy snapshot preservation on attendance records (immutable historical baseline)
- [x] Verified full regression test suite (mvn clean test PASSED: 32/32 clean tests)
- [x] Created docs/PHASE_1G_SHIFT_MANAGEMENT.md

### Phase 1H: Leave Management & Holiday Calendar Engine
- [x] Created V7__leave_management_schema.sql (holidays, leave_types, leave_policies, employee_leave_balances, leave_ledger, leave_applications, leave_approval_steps with FORCE RLS, additive leave_application_id on attendance_records)
- [x] Implemented Holiday, LeaveType, LeavePolicy, EmployeeLeaveBalance, LeaveLedger, LeaveApplication, LeaveApprovalStep JPA entities & repositories
- [x] Holiday calendar management supporting company-wide and branch-scoped holidays
- [x] Leave types and policies configuration (annual allocation, monthly accrual, carry-forward, negative balance limits)
- [x] Idempotent accrual engine and double-entry immutable audit ledger (LeaveLedger)
- [x] Dynamic chargeable leave days calculation deducting weekly offs and branch holidays
- [x] Leave applications (full-day, half-day) with overlap rejection and balance reservation
- [x] Approval/rejection workflow with reporting manager routing and self-approval prevention
- [x] Leave cancellation with automatic ledger credit reversal
- [x] Attendance integration (leave lookup on date and status ON_LEAVE without deleting punches)
- [x] Leave reports by employee, branch, department, and multi-filter summary
- [x] Verified full regression test suite (mvn clean test PASSED: 40/40 clean tests)
- [x] Created docs/PHASE_1H_LEAVE_MANAGEMENT.md

### Phase 1I: Payroll & Salary Structure Engine (Upcoming)
- [ ] Salary components (Basic, HRA, DA, Special Allowance, PF, ESI, TDS, Professional Tax)
- [ ] Employee salary structure configuration and versioning
- [ ] Monthly payroll processing and attendance/leave integration (LOP calculation)
- [ ] Payslip generation and disbursement records

---

## Log of Changes
- **2026-10-31**:
  - Initialized Git repository, configured root .gitignore, and set up master documentation files.
  - Completed Phase 1A architecture audit, Phase 1B schema refactor, Phase 1C secure JWT authentication, and Phase 1D company onboarding.
  - Implemented Phase 1E: Employee data architecture (V4__employee_management_schema.sql), employee CRUD, multi-criteria filtering, document metadata, audit history tracking, and identity masking. Verified test suite with 21/21 tests passing.
  - Implemented Phase 1F: Attendance & Geofencing Engine (V5__attendance_engine_schema.sql), Haversine GPS geofencing, selfie audit metadata, break management, and attendance corrections. Verified test suite with 24/24 tests passing.
  - Implemented Phase 1G: Shift Management & Scheduling Engine (V6__shift_management_schema.sql), overnight & DST calculations, interval overlap conflict detection, draft roster publishing, shift change requests, and multi-session attendance integration. Verified regression suite with 32/32 tests passing.
  - Implemented Phase 1H: Leave Management & Holiday Calendar Engine (V7__leave_management_schema.sql), company & branch holiday calendar, idempotent monthly accrual, immutable leave ledger, non-working day deduction, approval workflows with self-approval prevention, and cancellation ledger reversal. Verified regression suite with 40/40 tests passing.
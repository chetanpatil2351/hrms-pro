# Changelog

All notable changes to the HRMS Pro project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]
### Added
- Repository initialization with Git and root .gitignore.
- Full-stack monorepo scaffolding (backend, web, mobile).
- Architecture audit document docs/PHASE_1A_ARCHITECTURE_AUDIT.md.
- Phase 1B schema refactoring migration V2__multi_tenant_rbac_schema.sql and JPA entities.
- Phase 1C Authentication and JWT Token Lifecycle (docs/PHASE_1C_AUTHENTICATION.md).
- Phase 1D Company Onboarding & Tenant Management (docs/PHASE_1D_COMPANY_ONBOARDING.md).
- Phase 1E Employee Management & Profiles (docs/PHASE_1E_EMPLOYEE_MANAGEMENT.md):
  - Flyway migration V4__employee_management_schema.sql with default-deny RLS on employees, employee_documents, and employee_audit_logs.
  - JPA entities: Employee, EmployeeDocument, EmployeeAuditLog.
  - Concurrent-safe unique employee code generation scoped per tenant.
  - Sensitive identity masking (PAN, Aadhaar) on retrieval.
  - Strict tenant boundary checks on branch, department, and reporting manager assignments.
  - Comprehensive Employee APIs: listing with pagination/search/filtering, self profile /me, status modification, document metadata tracking, and audit history.
  - Automated test suite EmployeeManagementTests passing 100% (21/21 tests passed across all modules).
- Phase 1F Attendance & Geofencing Engine (docs/PHASE_1F_ATTENDANCE_ENGINE.md):
  - Flyway migration V5__attendance_engine_schema.sql with default-deny RLS on attendance_locations, attendance_records, attendance_events, attendance_breaks, and attendance_corrections.
  - JPA entities: AttendanceLocation, AttendanceRecord, AttendanceEvent, AttendanceBreak, AttendanceCorrection.
  - Haversine GPS geofence validation engine with STRICT and FLAG_OUTSIDE enforcement modes.
  - Selfie metadata verification and audit trail logging per punch.
  - Break session tracking with start/end timestamps and net working duration calculation.
  - Punch correction request and review/approval workflow.
  - REST endpoints for locations, check-in, check-out, break sessions, and attendance corrections.
  - Full test suite verified with 100% pass rate (24/24 clean tests passing).
- Phase 1G Shift Management & Scheduling Engine (docs/PHASE_1G_SHIFT_MANAGEMENT.md):
  - Pre-implementation audit verified `app.current_tenant_id` setting consistency across code and all migrations.
  - Flyway migration V6__shift_management_schema.sql with default-deny RLS on shift_templates, employee_shift_assignments, shift_rosters, shift_change_requests, and shift_assignment_history; additive multi-session and shift snapshot columns on attendance_records.
  - JPA entities: ShiftTemplate, EmployeeShiftAssignment, ShiftRoster, ShiftChangeRequest, ShiftAssignmentHistory.
  - Shift template CRUD with unique code per tenant, JSON weekly off days validation, and timezone-aware scheduling.
  - Overnight shifts crossing midnight and DST spring-forward (23h) / fall-back (25h) duration calculation.
  - Employee shift assignments with interval overlap detection for bounded and open-ended ranges.
  - Bulk shift assignment endpoint with skip-conflicting flag and immutable audit logging.
  - Roster generation in DRAFT status with weekly off detection, publish, and cancellation workflows.
  - Employee shift change request submission, manager review, and self-approval prevention.
  - Multi-session split shifts on same date with sequential session_number discriminator.
  - Shift policy snapshot preservation on attendance records for immutable historical baseline.
  - Full test suite verified with 100% pass rate (32/32 clean tests passing).
- Phase 1H Leave Management & Holiday Calendar Engine (docs/PHASE_1H_LEAVE_MANAGEMENT.md):
  - Flyway migration V7__leave_management_schema.sql with default-deny RLS on holidays, leave_types, leave_policies, employee_leave_balances, leave_ledger, leave_applications, and leave_approval_steps; additive leave_application_id column on attendance_records.
  - JPA entities: Holiday, LeaveType, LeavePolicy, EmployeeLeaveBalance, LeaveLedger, LeaveApplication, LeaveApprovalStep.
  - Holiday calendar operations supporting company-wide and branch-specific holidays.
  - Leave types and policies configuration with annual allocation, monthly accrual, carry-forward, and negative balance thresholds.
  - Idempotent monthly accrual engine and immutable double-entry leave ledger (LeaveLedger).
  - Chargeable leave days calculation dynamically deducting weekly offs and branch holidays based on roster and shift policies.
  - Leave application submission with overlap rejection, insufficient balance checks, and balance reservation.
  - Manager review workflow with reporting manager routing, comments, and self-approval prevention.
  - Approved leave cancellation with automatic ledger credit reversal.
  - Attendance integration preserving punch history and mapping approved leave days.
  - Multi-dimensional reporting endpoints by employee, branch, department, and summary filter.
  - Full test suite verified with 100% pass rate (40/40 clean tests passing).
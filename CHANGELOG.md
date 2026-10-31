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
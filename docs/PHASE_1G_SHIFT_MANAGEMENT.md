# Phase 1G: Shift Management & Scheduling Engine Architecture & Verification

## 1. Executive Summary
Phase 1G delivers the enterprise workforce shift management and scheduling engine for HRMS Pro. It introduces master shift templates, interval-aware employee shift assignments, multi-week roster generation in draft/published lifecycle, employee shift change request workflows with self-approval prevention, and seamless attendance integration supporting split shifts, overnight shifts crossing midnight, and DST-aware calculations.

---

## 2. Pre-Implementation Audit & Security Results
- **Tenant Context Setting Verification**: Confirmed that TenantContextAspect.java, Flyway migrations V2 through V5, and the new V6 migration all consistently use pp.current_tenant_id. The mention of pp.current_tenant in Phase 1F documentation was a documentation typo only and did not reflect any inconsistency in the code or database schema.
- **Row Level Security (RLS)**: Enforced default-deny RLS on all 5 new tables (shift_templates, employee_shift_assignments, shift_rosters, shift_change_requests, shift_assignment_history) using pp.current_tenant_id.
- **Database Engine RLS Status**: Tested locally using H2 in PostgreSQL compatibility mode. Real PostgreSQL RLS verification is marked as PENDING until live Supabase PostgreSQL connection is attached.

---

## 3. Database Schema & Architecture (V6__shift_management_schema.sql)

### 3.1 ER Model Summary
- **shift_templates**: Defines reusable shift configuration with code, start_time, end_time, is_overnight, timezone (IANA), total_scheduled_minutes, grace_period_minutes, late/early policies, break policies, and weekly_off_days (JSON array of day names: ['SATURDAY', 'SUNDAY']).
- **employee_shift_assignments**: Records employee shift allocation with effective_start, effective_end (nullable for open-ended), assignment_type (PERMANENT, TEMPORARY, ROTATING), and rotation parameters.
- **shift_rosters**: Daily schedule records per employee per date in DRAFT or PUBLISHED status with precomputed UTC start/end times and immutable shift_snapshot.
- **shift_change_requests**: Employee-initiated change requests with reason, requested shift/dates, and reviewer audit fields.
- **shift_assignment_history**: Immutable audit trail capturing every assignment modification, cancellation, and creation event.
- **attendance_records (additive)**: Added session_number, ssigned_shift_id, shift_snapshot, scheduled_start_utc, scheduled_end_utc, scheduled_work_minutes, late_by_minutes, early_departure_by_minutes, and unique constraint uk_attendance_session (tenant_id, employee_id, attendance_date, session_number).

---

## 4. Key Architectural Policies & Calculations

### 4.1 Timezone & Daylight Saving Time (DST) Policy
- All scheduled times use IANA ZoneId values (e.g. Asia/Kolkata, America/New_York).
- Calculations use ZonedDateTime and Duration.between() on Instant timestamps, never simple time-of-day subtraction.
- **Spring-Forward (23-Hour Day)**: When local clocks advance across the transition hour, scheduled minutes reflect the actual wall-clock duration (e.g. 1 AM to 5 AM spanning spring-forward is computed as 180 minutes).
- **Fall-Back (25-Hour Day)**: When clocks fall back, the extra hour is captured in the duration (e.g. 1 AM to 5 AM spanning fall-back is computed as 300 minutes). Attendance records actual worked time; excess hours are flagged as overtime candidates but not auto-credited as paid overtime.

### 4.2 Split Shifts & Multi-Session Support
- Employees can have multiple check-in sessions per calendar date.
- session_number is sequentially allocated per employee per date.
- Overlapping active sessions are blocked while allowing closed completed sessions.

### 4.3 Historical Preservation Rule
- Once an attendance record or published roster is created, its shift snapshot, scheduled times, and policy parameters are permanently preserved.
- Modifying a ShiftTemplate template does NOT retroactively alter historical attendance records or published rosters.

---

## 5. REST APIs Implemented

| Method | Endpoint | Authorized Roles | Description |
|---|---|---|---|
| POST | /api/v1/shifts | COMPANY_ADMIN, HR_MANAGER | Create shift template |
| GET | /api/v1/shifts | COMPANY_ADMIN, HR_MANAGER, BRANCH_MANAGER, ATTENDANCE_MANAGER, PAYROLL_MANAGER | List templates |
| GET | /api/v1/shifts/{id} | COMPANY_ADMIN, HR_MANAGER, BRANCH_MANAGER, ATTENDANCE_MANAGER, PAYROLL_MANAGER | Get template by ID |
| PUT | /api/v1/shifts/{id} | COMPANY_ADMIN, HR_MANAGER | Update template |
| PATCH | /api/v1/shifts/{id}/status | COMPANY_ADMIN, HR_MANAGER | Activate / Deactivate template |
| POST | /api/v1/shifts/assignments | COMPANY_ADMIN, HR_MANAGER, BRANCH_MANAGER, ATTENDANCE_MANAGER | Assign shift to employee |
| POST | /api/v1/shifts/assignments/bulk | COMPANY_ADMIN, HR_MANAGER | Bulk assign shift with skip option |
| GET | /api/v1/shifts/assignments | COMPANY_ADMIN, HR_MANAGER, BRANCH_MANAGER, ATTENDANCE_MANAGER | List assignments |
| GET | /api/v1/shifts/assignments/employee/{id} | All managers + EMPLOYEE | List employee assignments |
| PUT | /api/v1/shifts/assignments/{id} | COMPANY_ADMIN, HR_MANAGER, BRANCH_MANAGER | Update assignment |
| PATCH | /api/v1/shifts/assignments/{id}/cancel | COMPANY_ADMIN, HR_MANAGER, BRANCH_MANAGER | Cancel assignment |
| POST | /api/v1/rosters/generate | COMPANY_ADMIN, HR_MANAGER | Generate roster in DRAFT status |
| POST | /api/v1/rosters/publish | COMPANY_ADMIN, HR_MANAGER | Publish roster batch |
| POST | /api/v1/rosters/cancel | COMPANY_ADMIN, HR_MANAGER | Void draft rosters |
| GET | /api/v1/rosters | COMPANY_ADMIN, HR_MANAGER, BRANCH_MANAGER | View published rosters |
| GET | /api/v1/rosters/employee/{id} | All managers + EMPLOYEE | View employee rosters |
| GET | /api/v1/rosters/branch/{id} | COMPANY_ADMIN, HR_MANAGER, BRANCH_MANAGER | View branch rosters |
| POST | /api/v1/shifts/change-requests | EMPLOYEE, COMPANY_ADMIN, HR_MANAGER | Submit change request |
| GET | /api/v1/shifts/change-requests/my-requests | EMPLOYEE, COMPANY_ADMIN, HR_MANAGER | List employee's own requests |
| GET | /api/v1/shifts/change-requests/pending | COMPANY_ADMIN, HR_MANAGER, BRANCH_MANAGER | View pending requests |
| POST | /api/v1/shifts/change-requests/{id}/approve | COMPANY_ADMIN, HR_MANAGER, BRANCH_MANAGER | Approve request & apply assignment |
| POST | /api/v1/shifts/change-requests/{id}/reject | COMPANY_ADMIN, HR_MANAGER, BRANCH_MANAGER | Reject change request |

---

## 6. Automated Test Results

- **Total Test Suite**: **32 tests** executed across Tenant, Auth, Employee, Attendance, and Shift modules.
- **Failures / Errors**: **0**.
- **Shift Suite (ShiftManagementAndSchedulingTests)**: 8/8 tests passed clean:
  1. Shift template CRUD and duplicate shift code rejection.
  2. Overnight shift duration calculation (22:00 to 06:00 = 480 mins).
  3. DST spring-forward (180 mins) and fall-back (300 mins) timezone arithmetic.
  4. Employee shift assignment and open-ended overlap rejection.
  5. Bulk shift assignment with skipConflicting flag.
  6. Roster generation in DRAFT status, weekly off detection, and publishing.
  7. Shift change request submission, self-approval prevention, and approval re-validation.
  8. Attendance integration: session_number allocation (multi-session split shifts), scheduled work minutes snapshot, and historical template immutability.
- **Build Status**: **BUILD SUCCESS** in 47.3s.

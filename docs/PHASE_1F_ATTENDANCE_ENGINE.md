# Phase 1F: Attendance & Geofencing Engine Architecture & Verification

## 1. Executive Summary
Phase 1F delivers the complete core backend attendance management engine for HRMS Pro. It provides robust check-in/check-out workflows, GPS geofencing with Haversine distance calculations, configurable branch boundary validation policies (STRICT vs. LENIENT/FLAG_OUTSIDE), break session tracking, and attendance correction request/approval workflows with full audit events.

All database tables are created under PostgreSQL Row-Level Security (RLS) with explicit default-deny policies enforcing multi-tenant isolation.

---

## 2. Database Schema & Architecture (V5__attendance_engine_schema.sql)

### 2.1 Entity Relationship Summary
- **attendance_locations**: Configured geofence coordinates (latitude, longitude), permitted radius in meters, and policy (STRICT, FLAG_OUTSIDE).
- **attendance_records**: Daily rollup record representing an employee work day. Contains computed net_work_duration_minutes, work status (PRESENT, HALF_DAY, INCOMPLETE, etc.), and geofence compliance flags.
- **attendance_events**: Immutable audit trail for every punch event (check-in, check-out, geofence audit) capturing GPS coordinates, accuracy, distance, and selfie verification metadata.
- **attendance_breaks**: Tracks break sessions (e.g. LUNCH, TEA, PERSONAL) with start/end timestamps and computed duration.
- **attendance_corrections**: Employee-initiated punch correction requests with manager approval/rejection lifecycle.

### 2.2 Row Level Security (RLS)
Every table enforces RLS (ALTER TABLE ... FORCE ROW LEVEL SECURITY;) using PostgreSQL session tenant filtering.

---

## 3. Geofencing & Haversine Distance Calculation
The engine calculates great-circle distance on the Earth surface between the user's reported punch location and assigned branch locations using the Haversine formula (R = 6,371,000 meters).

### Policy Modes:
1. **STRICT**: If calculated distance > radiusMeters (accounting for client accuracy), punch is rejected with an AttendanceException: 'Check-in outside permitted geofence boundary'.
2. **FLAG_OUTSIDE**: Punch is permitted, but marked with isInsideGeofence = false and isGeofenceViolation = true on the record for manager review.

---

## 4. Break & Duration Calculation Rules
1. **Total Duration**: Difference in minutes between checkInTime and checkOutTime.
2. **Break Duration**: Sum of all closed attendance_breaks in minutes.
3. **Net Work Duration**: Net Work Minutes = Total Minutes - Total Break Minutes.
4. **Attendance Status Rules**:
   - >= 480 minutes (8 hours) -> PRESENT
   - >= 240 minutes (4 hours) and < 480 -> HALF_DAY
   - < 240 minutes -> INCOMPLETE

---

## 5. REST API Endpoints
- POST /api/v1/attendance/locations (TENANT_ADMIN, HR_MANAGER)
- POST /api/v1/attendance/check-in (EMPLOYEE, TENANT_ADMIN)
- POST /api/v1/attendance/check-out (EMPLOYEE, TENANT_ADMIN)
- GET /api/v1/attendance/me/today (EMPLOYEE)
- GET /api/v1/attendance/me/history (EMPLOYEE)
- POST /api/v1/attendance/breaks/start (EMPLOYEE)
- POST /api/v1/attendance/breaks/end (EMPLOYEE)
- POST /api/v1/attendance/corrections (EMPLOYEE)
- POST /api/v1/attendance/corrections/{id}/review (TENANT_ADMIN, HR_MANAGER)

---

## 6. Verification & Automated Test Results
- Total tests in suite: 24 tests across all modules (Tenant, Auth, Employee, Attendance).
- AttendanceAndGeofencingTests: 3/3 passed clean.
- Build status: BUILD SUCCESS.
- Live PostgreSQL RLS verification: PENDING (requires live Supabase instance; tested against H2 in PostgreSQL compatibility mode).

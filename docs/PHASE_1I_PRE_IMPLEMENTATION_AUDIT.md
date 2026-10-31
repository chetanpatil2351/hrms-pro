# Phase 1I: Payroll & Salary Structure Engine — Pre-Implementation Audit

## 1. Audit Overview
Date: 2026-10-31
Auditor: Senior Java Spring Boot Architect & Payroll Systems Engineer
Target: HRMS Pro Monorepo Backend (ackend/)

---

## 2. Codebase & Module Inspection

### 2.1 Employee & Tenant Architecture
- **Employee Entity (Employee.java)**: Contains joining_date, employment_status (ACTIVE, PROBATION, ON_LEAVE, RESIGNED, TERMINATED), branch, department, and reporting manager relationships.
- **Tenant & Membership**: Multi-tenant isolation verified; memberships map users to tenant-scoped roles (COMPANY_ADMIN, HR_MANAGER, PAYROLL_MANAGER, BRANCH_MANAGER, EMPLOYEE, ACCOUNTANT).
- **Security & Authorization**: All operations must resolve the tenant and actor identity from TenantContext.getTenantId() and UserPrincipal.getUserId(), never trusting incoming request payloads.

### 2.2 Attendance & Shift Integration
- **Attendance Record**: Contains ttendance_date, status (PRESENT, HALF_DAY, INCOMPLETE, ON_LEAVE), work_duration_minutes, 
et_work_duration_minutes, scheduled_work_minutes, late_by_minutes, early_departure_by_minutes.
- **Overtime Data**: Can be derived from excess worked minutes over scheduled work minutes where policy permits.
- **Shift Snapshots**: Preserved on attendance records; published rosters lock scheduled daily intervals.

### 2.3 Leave & Holiday Calendar Integration
- **Approved Leaves**: Tracked in leave_applications with is_paid status from leave_types. Unpaid leaves (Loss of Pay / LOP) directly reduce payable days or salary earnings.
- **Holiday Calendar**: Holiday entity tracks public, restricted, and branch-scoped non-working days.

### 2.4 Database Schema & RLS Conventions
- **Migrations**: V1 through V7 successfully applied in order.
- **Next Migration**: V8__payroll_salary_structure_schema.sql.
- **Tenant RLS Setting**: Must strictly use pp.current_tenant_id on all new tenant-scoped tables with ENABLE ROW LEVEL SECURITY and FORCE ROW LEVEL SECURITY.

---

## 3. Identified Integration Risks & Mitigation Strategies

| Risk Area | Specific Threat | Architectural Mitigation |
|---|---|---|
| Overlapping Salary Structures | Multiple active salary structures for an employee during the same date period causing conflicting rates | Implement strict interval overlap query in EmployeeSalaryStructureRepository rejecting intersecting active assignments |
| Duplicate Payroll Runs | Accidental concurrent or re-run payroll creating duplicate pay records | Unique constraint (tenant_id, payroll_period_id, run_type) and pessimistic lock on period state transition |
| Unpaid Absence Misclassification | Treating days without attendance punches as unpaid without verifying holidays/weekly-offs/approved leaves | Payroll calculation engine explicitly cross-checks published rosters, approved leaves, and holiday calendars before marking a day as unpaid LOP |
| Historical Recalculation | Modifying a salary component or template altering historical pay slips | Full calculation snapshots (payroll_calculation_snapshots) and component line items stored immutably upon payroll finalization |
| Statutory Rule Changes | Hardcoded EPF/ESI/PTax rates becoming obsolete | Configurable statutory_rules table with versioning, applicability thresholds, wage ceilings, and employee/employer rates |
| Cross-Tenant & Self-Access Leakage | Employees accessing peers' payslips or managers viewing other branches | Server-side role and tenant checks in controller & service layers; employee ID resolved from token identity for personal payslip access |

---

## 4. Implementation Plan
1. Create Flyway Migration V8__payroll_salary_structure_schema.sql.
2. Create JPA Entities & Repositories in com.hrmspro.modules.payroll.
3. Implement Salary Structure Engine, Employee Salary Assignment, and Revision History.
4. Implement Payroll Period & Run Workflow (DRAFT -> PROCESSING -> REVIEW -> APPROVED -> FINALIZED -> PAID).
5. Implement Deterministic Calculation Engine with Attendance, Leave (LOP), and Configurable Statutory Deductions (EPF, ESI, Professional Tax, TDS).
6. Implement REST Controllers for Salary Structures, Payroll Runs, Adjustments, and Payslips.
7. Implement comprehensive automated test suite and execute full project regression.

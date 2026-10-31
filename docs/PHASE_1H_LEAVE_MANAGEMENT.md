# Phase 1H: Leave Management & Holiday Calendar Engine Architecture & Verification

## 1. Executive Summary
Phase 1H implements the enterprise Leave Management & Holiday Calendar Engine for HRMS Pro. It provides configurable leave types (Paid, Unpaid, Annual, Sick, Casual, Maternity, Comp-Off), year-scoped leave policies with monthly accrual and carry-forward rules, an immutable double-entry style leave ledger, holiday calendars supporting company-wide and branch-scoped holidays, a non-working day deduction engine, full/half-day leave applications with multi-level approval workflows, and seamless attendance integration.

---

## 2. Multi-Tenant Architecture & RLS Security
- **Tenant Setting**: Strictly follows pp.current_tenant_id consistently across all new tables and queries.
- **Row-Level Security**: Enabled and forced default-deny RLS across all 7 new tables:
  - holidays`n  - leave_types`n  - leave_policies`n  - employee_leave_balances`n  - leave_ledger`n  - leave_applications`n  - leave_approval_steps`n- **Status**: Tested in local H2 in PostgreSQL compatibility mode. Live Supabase PostgreSQL RLS verification is pending deployment.

---

## 3. Database Schema (V7__leave_management_schema.sql)

### 3.1 Entity Relationship Diagram
`mermaid
erDiagram
    TENANTS ||--o{ HOLIDAYS : owns
    BRANCHES ||--o{ HOLIDAYS : overrides
    TENANTS ||--o{ LEAVE_TYPES : configures
    LEAVE_TYPES ||--o{ LEAVE_POLICIES : specifies
    EMPLOYEES ||--o{ EMPLOYEE_LEAVE_BALANCES : tracks
    LEAVE_TYPES ||--o{ EMPLOYEE_LEAVE_BALANCES : for
    EMPLOYEES ||--o{ LEAVE_APPLICATIONS : submits
    LEAVE_TYPES ||--o{ LEAVE_APPLICATIONS : categorizes
    LEAVE_APPLICATIONS ||--o{ LEAVE_APPROVAL_STEPS : audits
    EMPLOYEES ||--o{ LEAVE_LEDGER : records
    LEAVE_APPLICATIONS ||--o{ LEAVE_LEDGER : triggers
    LEAVE_APPLICATIONS ||--o{ ATTENDANCE_RECORDS : links
``n
---

## 4. Key Engines & Policies

### 4.1 Holiday Calendar Engine
- Supports both company-wide holidays (ranch_id IS NULL) and branch-specific holidays (ranch_id = :branchId).
- Classifies holidays as PUBLIC, RESTRICTED, OPTIONAL, or COMPANY.
- Soft deletion deactivates holidays without deleting historical references.

### 4.2 Leave Day Calculation Engine
- Dynamically computes chargeable leave days between start and end dates.
- Evaluates employee shift templates and published rosters to detect weekly offs.
- Cross-references the applicable holiday calendar for the employee's branch.
- Deducts weekly offs and holidays from the requested date range unless the policy explicitly counts them.
- Directly handles half-day requests (0.50 days) with morning/afternoon session tags.

### 4.3 Idempotent Accrual & Double-Entry Ledger
- Leave balances maintain live values (opening_balance, ccrued_amount, credited_amount, used_amount, pending_approval_amount, expired_amount, carried_forward_amount).
- Every balance mutation creates an immutable record in leave_ledger with unique idempotency_key (e.g., ACCRUAL_2026_01_{type}_{emp}).
- Deductions are reserved in pending_approval_amount at application time and debited from used_amount upon approval.
- Cancellation of approved leaves triggers a CREDIT_REVERSAL transaction in the ledger.

### 4.4 Approval Workflow & Self-Approval Prevention
- Applications are routed to the employee's reporting manager.
- Strict server-side check prevents employees from approving or rejecting their own leave requests.
- Revalidates balance and overlaps at approval time.

---

## 5. REST APIs Implemented

| Method | Endpoint | Authorized Roles | Description |
|---|---|---|---|
| POST | /api/v1/holidays | COMPANY_ADMIN, HR_MANAGER | Create company or branch holiday |
| GET | /api/v1/holidays | All roles | List holidays |
| GET | /api/v1/holidays/{id} | All roles | Get holiday by ID |
| PUT | /api/v1/holidays/{id} | COMPANY_ADMIN, HR_MANAGER | Update holiday |
| DELETE | /api/v1/holidays/{id} | COMPANY_ADMIN, HR_MANAGER | Soft-delete / deactivate holiday |
| POST | /api/v1/leaves/types | COMPANY_ADMIN, HR_MANAGER | Create leave type |
| GET | /api/v1/leaves/types | All roles | List active leave types |
| POST | /api/v1/leaves/policies | COMPANY_ADMIN, HR_MANAGER | Configure leave policy |
| POST | /api/v1/leaves/apply | EMPLOYEE, managers | Apply for leave (full/half day) |
| GET | /api/v1/leaves/my-requests | EMPLOYEE, managers | View personal leave requests |
| GET | /api/v1/leaves/my-balance | EMPLOYEE, managers | View personal leave balances |
| GET | /api/v1/leaves/{id} | All roles | Get leave application details |
| POST | /api/v1/leaves/{id}/cancel | EMPLOYEE, COMPANY_ADMIN, HR_MANAGER | Cancel leave application |
| GET | /api/v1/leaves/approvals/pending | Managers, HR, Admins | List pending approvals |
| POST | /api/v1/leaves/{id}/approve | Managers, HR, Admins | Approve leave & debit ledger |
| POST | /api/v1/leaves/{id}/reject | Managers, HR, Admins | Reject leave & release pending balance |
| POST | /api/v1/leaves/adjustments | COMPANY_ADMIN, HR_MANAGER | Manual HR balance adjustment |
| GET | /api/v1/leaves/reports/employee/{id} | Managers, HR, Admins, Payroll | Employee leave report |
| GET | /api/v1/leaves/reports/branch/{id} | Managers, HR, Admins, Payroll | Branch leave report |
| GET | /api/v1/leaves/reports/department/{id} | Managers, HR, Admins, Payroll | Department leave report |
| GET | /api/v1/leaves/reports/summary | Managers, HR, Admins, Payroll, Accountant | Summary report with multi-filters |

---

## 6. Automated Verification Results

- **Total Test Suite**: **40 tests** executed across Tenant, Auth, Employee, Attendance, Shift, and Leave modules.
- **Failures / Errors**: **0**.
- **Leave Suite (LeaveManagementAndHolidayCalendarTests)**: 8/8 tests passed clean:
  1. Holiday calendar creation, recurring settings, and branch scoping.
  2. Leave type creation, duplicate code rejection, and annual policy configuration.
  3. Monthly accrual processing, idempotency keys, and ledger audit logging.
  4. Chargeable leave days calculation excluding weekly offs and branch holidays.
  5. Leave application submission, overlap rejection, and balance sufficiency validation.
  6. Leave approval workflow, self-approval prevention, and ledger debit.
  7. Leave cancellation and double-entry ledger credit reversal.
  8. Half-day leave application (0.50 days) and report generation.
- **Build Result**: **BUILD SUCCESS** in 1m 11s.

# Phase 1I: Payroll & Salary Structure Engine

## 1. Overview
Phase 1I implements the enterprise payroll and compensation engine for HRMS Pro. It provides multi-component salary structure templates, individual employee salary assignments with versioned revisions, statutory tax and compliance rules (EPF, ESI, Professional Tax, TDS), deterministic payroll calculations with cross-module integration (Attendance, Roster, Loss of Pay / LOP Unpaid Leaves), multi-step payroll lifecycle workflows, and automated payslip generation.

## 2. Database Schema (Flyway V8)
The additive migration `V8__payroll_salary_structure_schema.sql` introduces 13 tables:
1. `salary_components`: Base earnings, deductions, and employer contributions (with calculation types FIXED_AMOUNT or PERCENTAGE).
2. `salary_structures`: Grouping templates for salary packages.
3. `salary_structure_components`: Many-to-many relationship linking components to structures with display ordering and default amounts/rates.
4. `employee_salary_structures`: Versioned salary assignments for employees with non-overlapping effective date ranges (`effective_from`, `effective_to`), Annual CTC, Monthly Gross, and Base Salary.
5. `salary_revision_history`: Immutable revision log capturing previous and new salary packages, revision reason, effective date, and revising authority.
6. `statutory_rules`: Configurable statutory rates and thresholds (EPF, ESI, Professional Tax, TDS) per tenant, state, and fiscal year.
7. `payroll_periods`: Monthly payroll cycle periods (`period_year`, `period_month`, `start_date`, `end_date`) tracked across statuses: `DRAFT` → `PROCESSING` → `REVIEW` → `APPROVED` → `FINALIZED` → `PAID`.
8. `payroll_runs`: Calculation batches (`REGULAR`, `SUPPLEMENTARY`) with monotonic run numbering.
9. `payroll_run_employees`: Individual employee results in a run with calendar days, payable days, LOP unpaid leave days, worked days, gross earnings, total deductions, employer contributions, and net pay.
10. `payroll_line_items`: Itemized line items per employee run for earnings, deductions, and employer contributions.
11. `payroll_calculation_snapshots`: Immutable JSON audit snapshot of calculation variables, salary components, and statutory rules for full reproducibility.
12. `payroll_adjustments`: Pre-payroll adjustments (advances, reimbursements, bonuses, arrears) with idempotency keys.
13. `payslips`: Official monthly payslip documents with unique payslip numbers, itemized breakdown in JSON, and payment status tracking.

All 13 tables are secured with default-deny Row Level Security (RLS) forced via PostgreSQL policies matching `app.current_tenant_id`.

## 3. Calculation Engine Logic
The `PayrollCalculationEngine` performs deterministic salary computations:
- **Calendar & Payable Days**: Identifies period length, mid-month joining dates, and calculates payable days after deducting approved unpaid leaves (Loss of Pay).
- **Pro-Rating Factor**: `payableDays / totalDaysInMonth`.
- **Earnings Breakdown**: Computes pro-rated Basic Salary, fixed allowances, and percentage-based allowances (e.g., HRA = 40% of Basic).
- **Statutory Deductions**:
  - **EPF**: 12% on Basic Salary capped at ₹15,000 wage ceiling (or configured rule).
  - **ESI**: 0.75% employee deduction and 3.25% employer contribution if monthly gross is within ₹21,000 ceiling.
  - **Professional Tax**: Configured state-wise slabs based on gross earnings threshold.
- **Adjustments**: Applies processed pre-payroll additions or deductions.
- **Net Salary**: `Gross Earnings - Total Deductions` (bounded at >= 0).
- **Audit Snapshot**: Generates full calculation input and state JSON stored in `payroll_calculation_snapshots`.

## 4. API Endpoints
### Salary Structure & Components (`/api/v1/payroll`)
- `POST /components`: Create a salary component (`ADMIN`, `HR_MANAGER`).
- `GET /components`: List active salary components.
- `POST /structures`: Create a salary structure template.
- `GET /structures`: List salary structures (paginated).
- `GET /structures/{id}`: Get salary structure details with mapped components.
- `POST /assignments`: Assign salary structure to employee with interval overlap validation and revision history tracking.
- `GET /assignments/employee/{employeeId}`: View employee salary history.
- `GET /revisions/employee/{employeeId}`: View salary revision log.

### Payroll Operations (`/api/v1/payroll`)
- `POST /statutory-rules`: Configure statutory tax rates (EPF, ESI, PT, TDS).
- `GET /statutory-rules`: List statutory rules by year.
- `POST /periods`: Create a payroll period for year/month (`DRAFT`).
- `GET /periods`: List payroll periods.
- `GET /periods/{id}`: Get period details and totals.
- `POST /periods/{id}/calculate`: Trigger calculation run for all eligible active employees. Moves period to `REVIEW`.
- `POST /periods/{id}/approve`: Approve calculated payroll. Moves period to `APPROVED`.
- `POST /periods/{id}/finalize`: Finalize and lock payroll period (`FINALIZED`). Freezes all employee run records and generates payslips.
- `POST /adjustments`: Add pre-payroll adjustment with idempotency key.

### Payslip Management (`/api/v1/payroll/payslips`)
- `GET /my-payslips`: Self-service view for authenticated employees.
- `GET /employee/{employeeId}`: HR/Admin view of employee payslips.
- `GET /{id}`: Fetch detailed payslip record by ID.

## 5. Automated Verification
All 46 automated integration tests pass cleanly:
- 40 existing tests across Auth, Tenant, Employee, Attendance, Shift, and Leave modules.
- 6 new Phase 1I tests in `PayrollAndSalaryStructureTests`:
  1. `testCreateSalaryStructure`: Component and structure creation.
  2. `testAssignSalaryStructureWithOverlapValidation`: Structure assignment and interval conflict rejection.
  3. `testSalaryRevisionHistory`: Annual CTC revision and audit history tracking.
  4. `testFullMonthPayrollCalculation`: Full month calculation with EPF and Professional Tax statutory deductions.
  5. `testLossOfPayPayrollCalculation`: Leave module integration with LOP unpaid leave deduction and pro-rated pay.
  6. `testPayrollLifecycleAndPayslipGeneration`: Complete lifecycle (`DRAFT` → `PROCESSING` → `REVIEW` → `APPROVED` → `FINALIZED`) and payslip generation.

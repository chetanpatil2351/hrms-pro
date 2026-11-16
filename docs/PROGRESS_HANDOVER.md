# PROGRESS HANDOVER – HRMS Pro Anti‑Fraud Backend Audit (Phase 2B)

## State of the Repository (as of 2026‑11‑09 19:25 +05:30)

| Item | Status |
|------|--------|
| **Maven Wrapper** | Scripts (`mvnw`, `mvnw.cmd`) and `maven-wrapper.properties` are present. The wrapper JAR (`maven-wrapper-0.5.6.jar`) is still missing from `backend/.mvn/wrapper/` due to sandbox network restrictions. It must be placed manually before any `./mvnw` commands can run.
| **V13 Migration** | Exists at `backend/src/main/resources/db/migration/V13__attendance_fraud_flags_schema.sql`. The migration creates `attendance_fraud_flags` and enables RLS, but the foreign‑key `reviewed_by UUID REFERENCES users(id)` references a non‑existent `users` table. The JPA entity maps `reviewedBy` to `AppUser` (`app_users` table). This mismatch will cause a migration failure on PostgreSQL.
| **V14 Migration** | Added to drop the wrong FK and create the correct one (`REFERENCES app_users(id) ON DELETE SET NULL`). This resolves the FK mismatch identified in V13.
| **V15 Migration** | Introduces tenant‑safe composite foreign‑key constraints for `employee_id`, `attendance_record_id`, and `attendance_event_id`. It also creates composite unique indexes on the referenced tables. The script uses `ON DELETE SET NULL` for nullable references and `ON DELETE CASCADE` for `employee_id`. This aligns with the multi‑tenant requirement.
| **V16 Migration** | Creates a non‑superuser role `hrms_app` with `NOINHERIT NOCREATEDB NOCREATEROLE NOREPLICATION`. It grants CONNECT, USAGE, and DML privileges, but **does NOT grant LOGIN** nor any sequence privileges. The role also lacks explicit `NO BYPASSRLS` (although the script sets it). Without LOGIN the application cannot authenticate as this role.
| **AttendanceFraudFlag Entity** | Extends `BaseTenantEntity` (provides `tenant_id`). JPA mappings for `employee`, `attendanceRecord`, `attendanceEvent`, and `reviewedBy` are correct, but there is no explicit `@JoinColumn` for the FK columns that include `tenant_id`. This is acceptable because the composite FK enforcement lives in the database (V15).
| **Datasource Configuration** | `application-dev.yml` uses `${DB_USERNAME:postgres}` (defaults to the superuser `postgres`). Therefore the running application currently connects as a superuser, which **bypasses RLS** and defeats the security model.
| **RLS Policy** | Defined in V13 (`CREATE POLICY rls_attendance_fraud_flags_tenant ...`). The policy checks `tenant_id = current_setting('app.current_tenant_id', true)::uuid`. No integration test has verified that the policy blocks access when the setting is missing or mismatched.
| **Tests** | `./mvnw clean test` (H2) passes 67 tests. No PostgreSQL‑specific integration tests exist for the new table, RLS, or the application role.
| **Uncommitted Changes** | `git status` shows many modified files (e.g., `AttendanceService.java`, `EmployeeController.java`) already staged. No new changes have been made in this audit.

## Immediate Issues Identified

1. **Foreign‑Key Mismatch** – V13 references `users(id)` while the entity expects `app_users`. Fixed by V14, but V13 is still part of the migration history; applying V13 on a fresh DB will fail unless the FK is corrected beforehand (e.g., by creating a forward migration that drops/recreates the table or by running V14 before V13 in a clean DB).
2. **Application Role Usage** – The app currently connects as `postgres` (superuser). This bypasses RLS and grants unrestricted privileges.
3. **Missing LOGIN for `hrms_app`** – V16 creates the role without `LOGIN`. The application cannot authenticate as this role.
4. **Sequence Permissions** – Tables that use serial/identity columns (e.g., `employees.id`, `attendance_records.id`) rely on sequences. `hrms_app` is not granted usage on those sequences, which will cause `permission denied for sequence` errors when inserting rows.
5. **RLS Verification** – No test confirms that the policy is enforced or that `hrms_app` does not have `BYPASSRLS`.
6. **Tenant‑Safe FK Constraints** – V15 adds composite constraints, but the referenced tables must retain the composite unique indexes (`uq_employees_tenant_id`, etc.). These indexes exist in the script, but we must ensure they are present in the current DB schema (they were added in earlier migrations).

## Proposed Corrections (Before Running Any Migrations)

| Area | Proposed Change |
|------|-----------------|
| **V13 / V14** | Create a **new forward migration** `V14b__fix_v13_fk.sql` that (a) drops the `attendance_fraud_flags` table if it exists, (b) recreates it using the corrected FK (`REFERENCES app_users(id)`) and the same column definitions, then (c) re‑applies the RLS policy. This guarantees a clean start on fresh environments without editing historical migrations.
| **V16** | Extend the migration to include `LOGIN` and a password (or rely on external secret). Example addition:
```sql
ALTER ROLE hrms_app WITH LOGIN PASSWORD 'CHANGE_ME';
GRANT USAGE ON SCHEMA public TO hrms_app; -- already present
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO hrms_app;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO hrms_app;
```
Also ensure `ALTER ROLE hrms_app NO BYPASSRLS;` remains.
| **Application datasource** | Change `application-dev.yml` (and `application-test.yml` if desired) to use the new role:
```yaml
spring:
  datasource:
    username: ${DB_USERNAME:hrms_app}
    password: ${DB_PASSWORD:CHANGE_ME}
```
Add the password to the environment or a secure vault.
| **RLS Integration Test** | Add `AttendanceFraudFlagRlsIntegrationTest` using Testcontainers PostgreSQL. The test should:
  * Start a container with the migrations applied.
  * Verify that without setting `app.current_tenant_id` a SELECT returns no rows.
  * Set tenant A, insert a flag for tenant A, then attempt to read with tenant B – expect zero rows.
  * Verify that the DB role (`hrms_app`) does **not** have `BYPASSRLS` (query `pg_roles`).
| **Tenant‑Safe FK Test** | Add `AttendanceFraudFlagTenantFkTest` that attempts to insert a fraud flag referencing an employee of a different tenant and expects a FK violation.
| **Sequence Permissions Test** | Simple insert into any table with a serial ID using the `hrms_app` role to confirm sequence usage is allowed.

## Updated Next Steps

1. **Add Maven wrapper JAR** manually (`backend/.mvn/wrapper/maven-wrapper-0.5.6.jar`).
2. **Create forward migration `V14b__fix_v13_fk.sql`** as described.
3. **Amend `V16__create_application_role.sql`** to grant `LOGIN` and `USAGE` on sequences.
4. **Update datasource configuration** (`application-dev.yml` and optionally `application-test.yml`) to use `hrms_app`.
5. **Implement `AttendanceFraudFlagController`** with CRUD endpoints, RBAC checks, and DTO mapping.
6. **Write PostgreSQL integration tests** for RLS, tenant‑safe FK, and sequence permissions.
7. **Run the full test suite** (`./mvnw clean test`) once the wrapper JAR is present and the datasource points to the test PostgreSQL container.
8. **Document the changes** in this handover file and in the project README (how to set up the role/password, how to run the integration tests).

## Handover Checklist

- [x] Maven wrapper scripts added (JAR pending).
- [ ] Provide `maven-wrapper-0.5.6.jar` manually.
- [ ] Add forward migration `V14b__fix_v13_fk.sql`.
- [ ] Extend `V16` to grant `LOGIN` and sequence usage.
- [ ] Update datasource to use `hrms_app`.
- [ ] Implement `AttendanceFraudFlagController`.
- [ ] Add PostgreSQL RLS and tenant‑FK integration tests.
- [ ] Verify role does not have `BYPASSRLS`.
- [ ] Run full test suite against PostgreSQL.

---

**Note:** All above changes are *proposed* only. No code has been committed or pushed yet. Please review and apply the migrations, configuration updates, and tests as a next step.

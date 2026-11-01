package com.hrmspro.modules.payroll;

import com.hrmspro.common.context.TenantContext;
import com.hrmspro.modules.auth.dto.RegisterRequest;
import com.hrmspro.modules.auth.dto.UserProfileDto;
import com.hrmspro.modules.auth.service.AuthService;
import com.hrmspro.modules.employee.Employee;
import com.hrmspro.modules.employee.EmployeeRepository;
import com.hrmspro.modules.employee.EmployeeService;
import com.hrmspro.modules.employee.dto.CreateEmployeeRequest;
import com.hrmspro.modules.employee.dto.EmployeeResponseDto;
import com.hrmspro.modules.leave.Holiday;
import com.hrmspro.modules.leave.HolidayRepository;
import com.hrmspro.modules.leave.LeaveApplication;
import com.hrmspro.modules.leave.LeaveApplicationRepository;
import com.hrmspro.modules.leave.LeaveType;
import com.hrmspro.modules.leave.LeaveTypeRepository;
import com.hrmspro.modules.payroll.dto.*;
import com.hrmspro.modules.shift.ShiftRoster;
import com.hrmspro.modules.shift.ShiftRosterRepository;
import com.hrmspro.modules.tenant.Branch;
import com.hrmspro.modules.tenant.TenantService;
import com.hrmspro.modules.tenant.dto.BranchDto;
import com.hrmspro.modules.tenant.dto.CreateTenantRequest;
import com.hrmspro.modules.tenant.dto.TenantResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import com.hrmspro.modules.payroll.EmployeeSalaryStructureRepository;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class PayrollStatutoryAndCalculationAccuracyTests {

    @Autowired
    private SalaryStructureService salaryStructureService;

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private PayrollSettingService payrollSettingService;

    @Autowired
    private AuthService authService;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private LeaveTypeRepository leaveTypeRepository;

    @Autowired
    private LeaveApplicationRepository leaveApplicationRepository;

    @Autowired
    private HolidayRepository holidayRepository;

    @Autowired
    private EmployeeSalaryStructureRepository employeeSalaryStructureRepository;

    @Autowired
    private PayrollPeriodRepository payrollPeriodRepository;

    private UUID tenantId;
    private UUID adminUserId;
    private BranchDto branch;
    private SalaryStructureDto standardStructure;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        employeeSalaryStructureRepository.deleteAll();
        UserProfileDto adminUser = authService.register(RegisterRequest.builder()
                .email("admin." + unique + "@statutory.com")
                .password("Password@123")
                .firstName("Admin")
                .lastName("Statutory")
                .build());
        adminUserId = adminUser.getId();

        TenantResponseDto tenant = tenantService.createTenant(
                adminUserId,
                CreateTenantRequest.builder()
                        .name("Statutory Test Corp " + unique)
                        .subdomain("statutory-" + unique)
                        .companyCode("STAT" + unique)
                        .email("admin." + unique + "@statutory.com")
                        .build());
        tenantId = tenant.getId();
        TenantContext.setTenantId(tenantId);

        branch = tenantService.createBranch(tenantId, BranchDto.builder()
                .name("HQ Statutory")
                .code("BR-" + unique)
                .build());

        // Standard components
        SalaryComponentDto basicComp = salaryStructureService.createComponent(SalaryComponentDto.builder()
                .name("Basic")
                .code("BASIC")
                .type("EARNING")
                .calculationType("PERCENTAGE")
                .calculationBase("BASIC")
                .percentageRate(BigDecimal.ONE)
                .build());

        SalaryComponentDto specialComp = salaryStructureService.createComponent(SalaryComponentDto.builder()
                .name("Special Allowance")
                .code("SPECIAL")
                .type("EARNING")
                .calculationType("FIXED_AMOUNT")
                .build());

        standardStructure = salaryStructureService.createStructure(SalaryStructureDto.builder()
                .name("Executive Structure")
                .code("EXEC_" + unique)
                .components(List.of(
                        SalaryStructureDto.ComponentItemDto.builder()
                                .componentId(basicComp.getId())
                                .percentageRate(BigDecimal.ONE)
                                .displayOrder(1)
                                .build(),
                        SalaryStructureDto.ComponentItemDto.builder()
                                .componentId(specialComp.getId())
                                .defaultAmount(BigDecimal.valueOf(10000.00))
                                .displayOrder(2)
                                .build()
                ))
                .build());

        // Configure pre- and post-Sept 2026 EPF rules
        payrollService.configureStatutoryRule(StatutoryRuleDto.builder()
                .ruleType("EPF")
                .effectiveYear(2026)
                .effectiveFrom(LocalDate.of(1900, 1, 1))
                .effectiveTo(LocalDate.of(2026, 9, 16))
                .jurisdiction("ALL")
                .sourceReference("S.O. 3502(D)")
                .ruleVersion("v1")
                .employeeRate(BigDecimal.valueOf(0.12))
                .employerRate(BigDecimal.valueOf(0.12))
                .wageCeiling(BigDecimal.valueOf(15000.00))
                .build());

        payrollService.configureStatutoryRule(StatutoryRuleDto.builder()
                .ruleType("EPF")
                .effectiveYear(2026)
                .effectiveFrom(LocalDate.of(2026, 9, 17))
                .effectiveTo(null)
                .jurisdiction("ALL")
                .sourceReference("Gazette S.O. 5109(E)")
                .ruleVersion("v2")
                .employeeRate(BigDecimal.valueOf(0.12))
                .employerRate(BigDecimal.valueOf(0.12))
                .wageCeiling(BigDecimal.valueOf(25000.00))
                .build());

        // ESI Rule (General ceiling 21,000, Disability 25,000 pending validation)
        payrollService.configureStatutoryRule(StatutoryRuleDto.builder()
                .ruleType("ESI")
                .effectiveYear(2026)
                .effectiveFrom(LocalDate.of(1900, 1, 1))
                .effectiveTo(null)
                .jurisdiction("ALL")
                .employeeRate(BigDecimal.valueOf(0.0075))
                .employerRate(BigDecimal.valueOf(0.0325))
                .wageCeiling(BigDecimal.valueOf(21000.00))
                .disabilityCeiling(BigDecimal.valueOf(25000.00))
                .build());
    }

    private Employee createEmployee(String code, LocalDate joinDate, BigDecimal basicSalary) {
        EmployeeResponseDto empDto = employeeService.createEmployee(tenantId, adminUserId, CreateEmployeeRequest.builder()
                .firstName("Emp_" + code)
                .lastName("Test")
                .workEmail("emp." + code + "@statutory.com")
                .joiningDate(joinDate)
                .employmentType("FULL_TIME")
                .designation("Engineer")
                .branchId(branch.getId())
                .build());
        Employee emp = employeeRepository.findById(empDto.getId()).orElseThrow();

        salaryStructureService.assignSalaryStructure(
                EmployeeSalaryStructureDto.builder()
                        .employeeId(emp.getId())
                        .salaryStructureId(standardStructure.getId())
                        .effectiveFrom(joinDate)
                        .annualCtc(basicSalary.multiply(BigDecimal.valueOf(12)).add(BigDecimal.valueOf(120000)))
                        .monthlyBaseSalary(basicSalary)
                        .build(),
                adminUserId
        );
        return emp;
    }

    @Test
    @DisplayName("Historical EPF: Prior to Sept 2026, EPF ceiling must be capped at 15,000")
    void testHistoricalEpfCeilingBeforeSept2026() {
        // Employee with basic 30,000 (above both 15k and 25k)
        Employee emp = createEmployee("HIST1", LocalDate.of(2026, 1, 1), BigDecimal.valueOf(30000.00));

        PayrollPeriodDto period = payrollService.createPeriod(PayrollPeriodDto.builder()
                .year(2026)
                .month(8) // August 2026: before 17 Sept 2026
                .build());

        PayrollRunResultDto run = payrollService.calculatePayrollRun(period.getId(), adminUserId);
        PayrollRunResultDto.EmployeePayrollSummaryDto summary = run.getEmployeeSummaries().get(0);

        // EPF capped at 15,000 -> 12% = 1,800.00
        assertThat(summary.getTotalDeductions()).isEqualByComparingTo("1800.00");
    }

    @Test
    @DisplayName("Post-Sept 2026 EPF: After 17 Sept 2026, EPF ceiling must cap at 25,000 for high earners")
    void testPostSept2026EpfCeiling() {
        // Employee with basic 30,000
        Employee emp = createEmployee("POST1", LocalDate.of(2026, 1, 1), BigDecimal.valueOf(30000.00));

        PayrollPeriodDto period = payrollService.createPeriod(PayrollPeriodDto.builder()
                .year(2026)
                .month(10) // October 2026: after 17 Sept 2026
                .build());

        PayrollRunResultDto run = payrollService.calculatePayrollRun(period.getId(), adminUserId);
        PayrollRunResultDto.EmployeePayrollSummaryDto summary = run.getEmployeeSummaries().get(0);

        // EPF capped at 25,000 -> 12% = 3,000.00
        assertThat(summary.getTotalDeductions()).isEqualByComparingTo("3000.00");
    }

    @Test
    @DisplayName("Intermediate Wage Scenario: Basic of 20,000 (between 15k and 25k)")
    void testIntermediateWageBetween15kAnd25k() {
        // Basic 20,000: In August capped at 15,000 (PF = 1,800). In October calculated on full 20,000 (PF = 2,400).
        Employee emp = createEmployee("MID1", LocalDate.of(2026, 1, 1), BigDecimal.valueOf(20000.00));

        PayrollPeriodDto augPeriod = payrollService.createPeriod(PayrollPeriodDto.builder()
                .year(2026)
                .month(8)
                .build());
        PayrollRunResultDto augRun = payrollService.calculatePayrollRun(augPeriod.getId(), adminUserId);
        assertThat(augRun.getEmployeeSummaries().get(0).getTotalDeductions()).isEqualByComparingTo("1800.00");

        PayrollPeriodDto octPeriod = payrollService.createPeriod(PayrollPeriodDto.builder()
                .year(2026)
                .month(10)
                .build());
        PayrollRunResultDto octRun = payrollService.calculatePayrollRun(octPeriod.getId(), adminUserId);
        assertThat(octRun.getEmployeeSummaries().get(0).getTotalDeductions()).isEqualByComparingTo("2400.00");
    }

    @Test
    @DisplayName("September 2026 Transition: Verify month of transition applies the 25,000 ceiling")
    void testSeptember2026Transition() {
        // Basic 30,000
        Employee emp = createEmployee("SEP1", LocalDate.of(2026, 1, 1), BigDecimal.valueOf(30000.00));

        PayrollPeriodDto sepPeriod = payrollService.createPeriod(PayrollPeriodDto.builder()
                .year(2026)
                .month(9)
                .build());

        PayrollRunResultDto run = payrollService.calculatePayrollRun(sepPeriod.getId(), adminUserId);
        PayrollRunResultDto.EmployeePayrollSummaryDto summary = run.getEmployeeSummaries().get(0);

        // Under statutory contribution-period rule, September end date (30 Sept) invokes v2 rule (25,000 ceiling)
        assertThat(summary.getTotalDeductions()).isEqualByComparingTo("3000.00");
    }

    @Test
    @DisplayName("Mid-Month Joining: Employee joining mid-month gets pro-rated salary and capped deductions")
    void testMidMonthJoining() {
        // Joins on 16th January (16 days out of 31)
        Employee emp = createEmployee("JOIN1", LocalDate.of(2026, 1, 16), BigDecimal.valueOf(31000.00));

        PayrollPeriodDto period = payrollService.createPeriod(PayrollPeriodDto.builder()
                .year(2026)
                .month(1)
                .build());

        PayrollRunResultDto run = payrollService.calculatePayrollRun(period.getId(), adminUserId);
        PayrollRunResultDto.EmployeePayrollSummaryDto summary = run.getEmployeeSummaries().get(0);

        assertThat(summary.getTotalDays()).isEqualTo(31);
        assertThat(summary.getPayableDays()).isEqualByComparingTo("16.00");

        // Pro-rated factor = 16 / 31
        // Basic = 31000 * (16 / 31) = 16000.00
        // In Jan 2026, ceiling is 15000 -> EPF employee = 15000 * 0.12 = 1800.00
        assertThat(summary.getTotalDeductions()).isEqualByComparingTo("1800.00");
    }

    @Test
    @DisplayName("LOP Double-Deduction Prevention: Ensure unpaid leave reduces payable days without double deductions")
    void testLossOfPayNoDoubleDeduction() {
        Employee emp = createEmployee("LOP1", LocalDate.of(2026, 1, 1), BigDecimal.valueOf(31000.00));

        LeaveType unpaidType = LeaveType.builder()
                .name("Unpaid Leave LOP Guard")
                .code("LOPG_" + UUID.randomUUID().toString().substring(0, 5))
                .isPaid(false)
                .category("UNPAID")
                .build();
        unpaidType.setTenantId(tenantId);
        leaveTypeRepository.save(unpaidType);

        // 5 days LOP
        LeaveApplication app = LeaveApplication.builder()
                .employee(emp)
                .leaveType(unpaidType)
                .startDate(LocalDate.of(2026, 1, 10))
                .endDate(LocalDate.of(2026, 1, 14))
                .isHalfDay(false)
                .totalDays(BigDecimal.valueOf(5))
                .reason("Approved unpaid leave for payroll LOP testing")
                .status("APPROVED")
                .build();
        app.setTenantId(tenantId);
        leaveApplicationRepository.save(app);

        PayrollPeriodDto period = payrollService.createPeriod(PayrollPeriodDto.builder()
                .year(2026)
                .month(1)
                .build());

        PayrollRunResultDto run = payrollService.calculatePayrollRun(period.getId(), adminUserId);
        PayrollRunResultDto.EmployeePayrollSummaryDto summary = run.getEmployeeSummaries().get(0);

        // 31 - 5 = 26 payable days
        assertThat(summary.getPayableDays()).isEqualByComparingTo("26.00");
        assertThat(summary.getUnpaidLeaveDays()).isEqualByComparingTo("5.00");

        // Gross earnings pro-rated: (31000 Basic + 10000 Special) * (26 / 31) = 41000 * 0.8387096... = 34387.10
        BigDecimal expectedGross = BigDecimal.valueOf(41000.00)
                .multiply(BigDecimal.valueOf(26))
                .divide(BigDecimal.valueOf(31), 2, java.math.RoundingMode.HALF_UP);
        assertThat(summary.getGrossEarnings()).isEqualByComparingTo(expectedGross);

        // Deductions apply to the pro-rated earnings only, no additional LOP deduction line item exists
        BigDecimal netExpected = summary.getGrossEarnings().subtract(summary.getTotalDeductions());
        assertThat(summary.getNetSalary()).isEqualByComparingTo(netExpected);
    }

    @Test
    @DisplayName("Proration Policies: Verify WORKING_DAYS excludes branch holidays and weekly offs")
    void testWorkingDaysProrationPolicy() {
        Employee emp = createEmployee("WRK1", LocalDate.of(2026, 1, 16), BigDecimal.valueOf(31000.00));

        // Configure branch holiday on 26 Jan (Republic Day)
        Holiday holiday = Holiday.builder()
                .name("Republic Day")
                .holidayDate(LocalDate.of(2026, 1, 26)) // A Monday
                .holidayType("PUBLIC")
                .branch(employeeRepository.findById(emp.getId()).orElseThrow().getBranch())
                .build();
        holiday.setTenantId(tenantId);
        holidayRepository.save(holiday);

        // Update tenant setting to WORKING_DAYS
        payrollSettingService.updateProrationPolicy("WORKING_DAYS");

        PayrollPeriodDto period = payrollService.createPeriod(PayrollPeriodDto.builder()
                .year(2026)
                .month(1)
                .build());

        PayrollRunResultDto run = payrollService.calculatePayrollRun(period.getId(), adminUserId);
        PayrollRunResultDto.EmployeePayrollSummaryDto summary = run.getEmployeeSummaries().get(0);

        // From 16 Jan to 31 Jan = 16 calendar days
        // Weekends: Jan 17(Sat), 18(Sun), 24(Sat), 25(Sun), 31(Sat) = 5 weekend days
        // Holiday: Jan 26 (Mon) = 1 holiday
        // Active working days for employee = 16 - 5 - 1 = 10 working days
        assertThat(summary.getPayableDays()).isEqualByComparingTo("10.00");

        // Whole month working days in Jan 2026:
        // 31 days - 9 weekend days (Jan 3,4, 10,11, 17,18, 24,25, 31) - 1 holiday (Jan 26) = 21 working days
        // Pay factor = 10 / 21 = 0.476190
        BigDecimal expectedPayFactor = BigDecimal.valueOf(10)
                .divide(BigDecimal.valueOf(21), 12, java.math.RoundingMode.HALF_UP);
        BigDecimal expectedBasic = BigDecimal.valueOf(31000.00)
                .multiply(expectedPayFactor)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal expectedSpecial = BigDecimal.valueOf(10000.00)
                .multiply(expectedPayFactor)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal expectedGross = expectedBasic.add(expectedSpecial);

        assertThat(summary.getGrossEarnings()).isEqualByComparingTo(expectedGross);
    }

    @Test
    @DisplayName("Proration Policies: Verify WORKING_DAYS handles mid-month exit and asserts exact pay factor")
    void testWorkingDaysProrationWithExitDate() {
        // Employee joins on 1 Jan 2026
        Employee emp = createEmployee("WRK_EXIT", LocalDate.of(2026, 1, 1), BigDecimal.valueOf(31000.00));

        // Mid-month exit on 15 Jan 2026: employee served until 15 Jan
        emp.setProbationEndDate(LocalDate.of(2026, 1, 15));
        employeeRepository.save(emp);

        // Update active assignment to end on 15 Jan
        List<EmployeeSalaryStructure> assignments = employeeSalaryStructureRepository
                .findByTenantIdAndEmployeeIdOrderByEffectiveFromDesc(tenantId, emp.getId());
        EmployeeSalaryStructure assignment = assignments.get(0);
        assignment.setEffectiveTo(LocalDate.of(2026, 1, 15));
        employeeSalaryStructureRepository.save(assignment);

        // Configure branch holiday on 26 Jan (Republic Day)
        Holiday holiday = Holiday.builder()
                .name("Republic Day")
                .holidayDate(LocalDate.of(2026, 1, 26))
                .holidayType("PUBLIC")
                .branch(employeeRepository.findById(emp.getId()).orElseThrow().getBranch())
                .build();
        holiday.setTenantId(tenantId);
        holidayRepository.save(holiday);

        payrollSettingService.updateProrationPolicy("WORKING_DAYS");

        PayrollPeriodDto period = payrollService.createPeriod(PayrollPeriodDto.builder()
                .year(2026)
                .month(1)
                .build());

        PayrollRunResultDto run = payrollService.calculatePayrollRun(period.getId(), adminUserId);
        PayrollRunResultDto.EmployeePayrollSummaryDto summary = run.getEmployeeSummaries().get(0);

        // Active period for employee: 1 Jan to 15 Jan = 15 calendar days
        // Weekends in Jan 1-15: Jan 3, 4, 10, 11 = 4 weekend days
        // Holidays in Jan 1-15: None (Republic day is on Jan 26)
        // Payable working days for employee = 15 - 4 = 11 working days
        assertThat(summary.getPayableDays()).isEqualByComparingTo("11.00");

        // Total working days in month = 21 (31 - 9 weekends - 1 holiday)
        // Expected pay factor = 11 / 21 = 0.523810
        BigDecimal expectedPayFactor = BigDecimal.valueOf(11)
                .divide(BigDecimal.valueOf(21), 12, java.math.RoundingMode.HALF_UP);
        BigDecimal expectedBasic = BigDecimal.valueOf(31000.00)
                .multiply(expectedPayFactor)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal expectedSpecial = BigDecimal.valueOf(10000.00)
                .multiply(expectedPayFactor)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal expectedGross = expectedBasic.add(expectedSpecial);

        assertThat(summary.getGrossEarnings()).isEqualByComparingTo(expectedGross);
    }

    @Test
    @DisplayName("ESI Disability Restriction: High earner above 21k does NOT get auto-enrolled under 25k disability ceiling")
    void testEsiDisabilityRestrictionPendingLegalValidation() {
        // Gross = 23,000 (Between 21,000 general ceiling and 25,000 disability ceiling)
        // Basic 13,000 + 10,000 Special = 23,000 Gross
        Employee emp = createEmployee("ESI1", LocalDate.of(2026, 1, 1), BigDecimal.valueOf(13000.00));

        PayrollPeriodDto period = payrollService.createPeriod(PayrollPeriodDto.builder()
                .year(2026)
                .month(1)
                .build());

        PayrollRunResultDto run = payrollService.calculatePayrollRun(period.getId(), adminUserId);
        PayrollRunResultDto.EmployeePayrollSummaryDto summary = run.getEmployeeSummaries().get(0);

        // Total Gross = 23,000 > 21,000 general ceiling.
        // As disability eligibility is strictly restricted pending legal verification,
        // no ESI deduction should occur. Total deductions should only be EPF (12% of 13,000 = 1,560.00).
        assertThat(summary.getTotalDeductions()).isEqualByComparingTo("1560.00");
    }

    @Test
    @DisplayName("Duplicate Payroll Processing: Attempting to recalculate finalized period must be prevented")
    void testDuplicatePayrollProcessingPrevention() {
        Employee emp = createEmployee("DUP1", LocalDate.of(2026, 1, 1), BigDecimal.valueOf(30000.00));

        PayrollPeriodDto period = payrollService.createPeriod(PayrollPeriodDto.builder()
                .year(2026)
                .month(1)
                .build());

        // Calculate and finalize
        payrollService.calculatePayrollRun(period.getId(), adminUserId);
        payrollService.approvePeriod(period.getId());
        payrollService.finalizePeriod(period.getId(), adminUserId);

        // Attempting to recalculate must fail
        assertThrows(IllegalStateException.class, () -> {
            payrollService.calculatePayrollRun(period.getId(), adminUserId);
        });
    }

    @Test
    @DisplayName("Tenant Isolation: Payroll calculations and rules must not cross tenant boundaries")
    void testTenantIsolation() {
        Employee emp1 = createEmployee("ISO1", LocalDate.of(2026, 1, 1), BigDecimal.valueOf(30000.00));

        // Create Tenant 2
        String u2 = UUID.randomUUID().toString().substring(0, 8);
        UserProfileDto adminUser2 = authService.register(RegisterRequest.builder()
                .email("admin." + u2 + "@tenant2.com")
                .password("Password@123")
                .firstName("Admin2")
                .lastName("Tenant2")
                .build());

        TenantResponseDto t2 = tenantService.createTenant(
                adminUser2.getId(),
                CreateTenantRequest.builder()
                        .name("Isolated Corp " + u2)
                        .subdomain("tenant2-" + u2)
                        .companyCode("ISO2" + u2)
                        .email("admin." + u2 + "@tenant2.com")
                        .build());

        // Set context to Tenant 2
        TenantContext.setTenantId(t2.getId());

        // Under Tenant 2, no statutory rules exist yet
        List<StatutoryRuleDto> t2Rules = payrollService.getStatutoryRules(2026);
        assertThat(t2Rules).isEmpty();

        // Tenant 2 cannot see or access employee from Tenant 1
        assertThrows(Exception.class, () -> {
            payrollService.getEmployeePayslips(emp1.getId());
        });
    }
}

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
import com.hrmspro.modules.leave.LeaveApplication;
import com.hrmspro.modules.leave.LeaveApplicationRepository;
import com.hrmspro.modules.leave.LeaveService;
import com.hrmspro.modules.leave.LeaveType;
import com.hrmspro.modules.leave.LeaveTypeRepository;
import com.hrmspro.modules.leave.dto.LeaveTypeDto;
import com.hrmspro.modules.payroll.dto.*;
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
public class PayrollAndSalaryStructureTests {

    @Autowired
    private SalaryStructureService salaryStructureService;

    @Autowired
    private PayrollService payrollService;

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

    private UUID tenantId;
    private UUID adminUserId;
    private Employee testEmployee;
    private SalaryStructureDto standardStructure;

    @BeforeEach
    void setUp() {
        // 1. Register Admin User
        String unique = UUID.randomUUID().toString().substring(0, 8);
        UserProfileDto adminUser = authService.register(RegisterRequest.builder()
                .email("admin." + unique + "@acme.com")
                .password("Password@123")
                .firstName("Admin")
                .lastName("Payroll")
                .build());
        adminUserId = adminUser.getId();

        // 2. Create Tenant
        TenantResponseDto tenant = tenantService.createTenant(
                adminUserId,
                CreateTenantRequest.builder()
                        .name("Acme Payroll " + unique)
                        .subdomain("acme-pay-" + unique)
                        .companyCode("ACME" + unique)
                        .email("admin." + unique + "@acme.com")
                        .build());
        tenantId = tenant.getId();
        TenantContext.setTenantId(tenantId);

        BranchDto branch = tenantService.createBranch(tenantId, BranchDto.builder()
                .name("HQ Branch")
                .code("HQ-" + unique)
                .build());

        // 3. Create Employee
        EmployeeResponseDto empDto = employeeService.createEmployee(tenantId, adminUserId, CreateEmployeeRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .workEmail("john.doe." + unique + "@acme.com")
                .joiningDate(LocalDate.of(2026, 1, 1))
                .employmentType("FULL_TIME")
                .designation("Senior Software Engineer")
                .branchId(branch.getId())
                .build());
        testEmployee = employeeRepository.findById(empDto.getId()).orElseThrow();

        // 4. Create Standard Salary Components
        SalaryComponentDto basicComp = salaryStructureService.createComponent(SalaryComponentDto.builder()
                .name("Basic Salary")
                .code("BASIC")
                .type("EARNING")
                .calculationType("PERCENTAGE")
                .calculationBase("BASIC")
                .percentageRate(BigDecimal.ONE)
                .isTaxable(true)
                .includeInGross(true)
                .includeInCTC(true)
                .build());

        SalaryComponentDto hraComp = salaryStructureService.createComponent(SalaryComponentDto.builder()
                .name("House Rent Allowance")
                .code("HRA")
                .type("EARNING")
                .calculationType("PERCENTAGE")
                .calculationBase("BASIC")
                .percentageRate(BigDecimal.valueOf(0.40))
                .isTaxable(true)
                .includeInGross(true)
                .includeInCTC(true)
                .build());

        SalaryComponentDto specialComp = salaryStructureService.createComponent(SalaryComponentDto.builder()
                .name("Special Allowance")
                .code("SPECIAL_ALLOWANCE")
                .type("EARNING")
                .calculationType("FIXED_AMOUNT")
                .isTaxable(true)
                .includeInGross(true)
                .includeInCTC(true)
                .build());

        // 5. Create Salary Structure
        standardStructure = salaryStructureService.createStructure(SalaryStructureDto.builder()
                .name("Standard Tech Structure")
                .code("TECH_STD_" + unique)
                .description("Standard tech structure for software engineers")
                .components(List.of(
                        SalaryStructureDto.ComponentItemDto.builder()
                                .componentId(basicComp.getId())
                                .percentageRate(BigDecimal.ONE)
                                .displayOrder(1)
                                .build(),
                        SalaryStructureDto.ComponentItemDto.builder()
                                .componentId(hraComp.getId())
                                .percentageRate(BigDecimal.valueOf(0.40))
                                .displayOrder(2)
                                .build(),
                        SalaryStructureDto.ComponentItemDto.builder()
                                .componentId(specialComp.getId())
                                .defaultAmount(BigDecimal.valueOf(10000.00))
                                .displayOrder(3)
                                .build()
                ))
                .build());

        // 6. Configure Statutory Rules (EPF & ESI)
        payrollService.configureStatutoryRule(StatutoryRuleDto.builder()
                .ruleType("EPF")
                .effectiveYear(2026)
                .employeeRate(BigDecimal.valueOf(0.12))
                .employerRate(BigDecimal.valueOf(0.12))
                .wageCeiling(BigDecimal.valueOf(15000.00))
                .minimumWageThreshold(BigDecimal.ZERO)
                .build());

        payrollService.configureStatutoryRule(StatutoryRuleDto.builder()
                .ruleType("ESI")
                .effectiveYear(2026)
                .employeeRate(BigDecimal.valueOf(0.0075))
                .employerRate(BigDecimal.valueOf(0.0325))
                .wageCeiling(BigDecimal.valueOf(21000.00))
                .minimumWageThreshold(BigDecimal.ZERO)
                .build());

        payrollService.configureStatutoryRule(StatutoryRuleDto.builder()
                .ruleType("PROFESSIONAL_TAX")
                .effectiveYear(2026)
                .employeeRate(BigDecimal.valueOf(200.00))
                .employerRate(BigDecimal.ZERO)
                .minimumWageThreshold(BigDecimal.valueOf(10000.00))
                .build());
    }

    @Test
    @DisplayName("Should successfully create salary structure and validate components")
    void testCreateSalaryStructure() {
        SalaryStructureDto fetched = salaryStructureService.getStructureById(standardStructure.getId());
        assertThat(fetched).isNotNull();
        assertThat(fetched.getName()).isEqualTo("Standard Tech Structure");
        assertThat(fetched.getComponents()).hasSize(3);
    }

    @Test
    @DisplayName("Should assign salary structure to employee and reject overlapping assignments")
    void testAssignSalaryStructureWithOverlapValidation() {
        // Valid assignment
        EmployeeSalaryStructureDto assignment = salaryStructureService.assignSalaryStructure(
                EmployeeSalaryStructureDto.builder()
                        .employeeId(testEmployee.getId())
                        .salaryStructureId(standardStructure.getId())
                        .effectiveFrom(LocalDate.of(2026, 1, 1))
                        .annualCtc(BigDecimal.valueOf(1200000.00)) // 12 LPA -> 100k gross/mo, 50k basic/mo
                        .monthlyBaseSalary(BigDecimal.valueOf(50000.00))
                        .build(),
                adminUserId
        );

        assertThat(assignment).isNotNull();
        assertThat(assignment.getVersion()).isEqualTo(1);
        assertThat(assignment.getAnnualCtc()).isEqualByComparingTo("1200000.00");
        assertThat(assignment.getMonthlyGross()).isEqualByComparingTo("100000.00");

        // Overlapping assignment must be rejected
        assertThrows(IllegalArgumentException.class, () -> {
            salaryStructureService.assignSalaryStructure(
                    EmployeeSalaryStructureDto.builder()
                            .employeeId(testEmployee.getId())
                            .salaryStructureId(standardStructure.getId())
                            .effectiveFrom(LocalDate.of(2026, 2, 1))
                            .annualCtc(BigDecimal.valueOf(1500000.00))
                            .build(),
                    adminUserId
            );
        });
    }

    @Test
    @DisplayName("Should track salary revision history when updated with a new effective date range")
    void testSalaryRevisionHistory() {
        // Initial assignment
        salaryStructureService.assignSalaryStructure(
                EmployeeSalaryStructureDto.builder()
                        .employeeId(testEmployee.getId())
                        .salaryStructureId(standardStructure.getId())
                        .effectiveFrom(LocalDate.of(2026, 1, 1))
                        .effectiveTo(LocalDate.of(2026, 6, 30))
                        .annualCtc(BigDecimal.valueOf(1200000.00))
                        .build(),
                adminUserId
        );

        // Revision from July 1
        EmployeeSalaryStructureDto revised = salaryStructureService.assignSalaryStructure(
                EmployeeSalaryStructureDto.builder()
                        .employeeId(testEmployee.getId())
                        .salaryStructureId(standardStructure.getId())
                        .effectiveFrom(LocalDate.of(2026, 7, 1))
                        .annualCtc(BigDecimal.valueOf(1500000.00))
                        .revisionReason("Annual Performance Promotion")
                        .build(),
                adminUserId
        );

        assertThat(revised.getVersion()).isEqualTo(2);
        List<SalaryRevisionDto> revisions = salaryStructureService.getEmployeeRevisionHistory(testEmployee.getId());
        assertThat(revisions).hasSize(2);
        assertThat(revisions.get(0).getNewAnnualCtc()).isEqualByComparingTo("1500000.00");
        assertThat(revisions.get(0).getRevisionReason()).isEqualTo("Annual Performance Promotion");
    }

    @Test
    @DisplayName("Should deterministically calculate payroll for full month with statutory deductions")
    void testFullMonthPayrollCalculation() {
        salaryStructureService.assignSalaryStructure(
                EmployeeSalaryStructureDto.builder()
                        .employeeId(testEmployee.getId())
                        .salaryStructureId(standardStructure.getId())
                        .effectiveFrom(LocalDate.of(2026, 1, 1))
                        .annualCtc(BigDecimal.valueOf(1200000.00))
                        .monthlyBaseSalary(BigDecimal.valueOf(50000.00))
                        .build(),
                adminUserId
        );

        // Create Payroll Period for January 2026 (31 days)
        PayrollPeriodDto period = payrollService.createPeriod(PayrollPeriodDto.builder()
                .year(2026)
                .month(1)
                .build());

        // Calculate run
        PayrollRunResultDto run = payrollService.calculatePayrollRun(period.getId(), adminUserId);

        assertThat(run).isNotNull();
        assertThat(run.getProcessedEmployees()).isEqualTo(1);
        PayrollRunResultDto.EmployeePayrollSummaryDto empSummary = run.getEmployeeSummaries().get(0);

        assertThat(empSummary.getTotalDays()).isEqualTo(31);
        assertThat(empSummary.getPayableDays()).isEqualByComparingTo("31.00");
        assertThat(empSummary.getUnpaidLeaveDays()).isEqualByComparingTo("0.00");

        // Basic = 50,000, HRA = 40% of Basic = 20,000, Special = 10,000 -> Gross = 80,000
        assertThat(empSummary.getGrossEarnings()).isEqualByComparingTo("80000.00");

        // Deductions:
        // EPF: 12% on min(50000, 15000 wage ceiling) = 1,800.00
        // PT: 200.00
        // Total deductions = 2,000.00
        assertThat(empSummary.getTotalDeductions()).isEqualByComparingTo("2000.00");

        // Net = 80,000 - 2,000 = 78,000.00
        assertThat(empSummary.getNetSalary()).isEqualByComparingTo("78000.00");
    }

    @Test
    @DisplayName("Should accurately deduct Loss of Pay (LOP) when employee takes unpaid leaves")
    void testLossOfPayPayrollCalculation() {
        salaryStructureService.assignSalaryStructure(
                EmployeeSalaryStructureDto.builder()
                        .employeeId(testEmployee.getId())
                        .salaryStructureId(standardStructure.getId())
                        .effectiveFrom(LocalDate.of(2026, 1, 1))
                        .annualCtc(BigDecimal.valueOf(1200000.00))
                        .monthlyBaseSalary(BigDecimal.valueOf(50000.00))
                        .build(),
                adminUserId
        );

        // Configure an UNPAID leave type and save approved application
        LeaveType unpaidType = LeaveType.builder()
                .name("Unpaid Leave LOP")
                .code("LOP_" + UUID.randomUUID().toString().substring(0, 5))
                .isPaid(false)
                .category("UNPAID")
                .build();
        unpaidType.setTenantId(tenantId);
        unpaidType = leaveTypeRepository.save(unpaidType);

        LeaveApplication application = LeaveApplication.builder()
                .employee(testEmployee)
                .leaveType(unpaidType)
                .startDate(LocalDate.of(2026, 1, 12))
                .endDate(LocalDate.of(2026, 1, 14))
                .isHalfDay(false)
                .totalDays(BigDecimal.valueOf(3))
                .reason("Personal emergency")
                .status("APPROVED")
                .build();
        application.setTenantId(tenantId);
        leaveApplicationRepository.save(application);

        PayrollPeriodDto period = payrollService.createPeriod(PayrollPeriodDto.builder()
                .year(2026)
                .month(1)
                .build());

        PayrollRunResultDto run = payrollService.calculatePayrollRun(period.getId(), adminUserId);
        PayrollRunResultDto.EmployeePayrollSummaryDto empSummary = run.getEmployeeSummaries().get(0);

        // Jan has 31 days. 3 days unpaid -> payableDays = 28.00
        assertThat(empSummary.getTotalDays()).isEqualTo(31);
        assertThat(empSummary.getUnpaidLeaveDays()).isEqualByComparingTo("3.00");
        assertThat(empSummary.getPayableDays()).isEqualByComparingTo("28.00");

        // Pro-rated factor: 28 / 31 = ~0.903226
        // Gross earnings should be less than 80,000.00
        assertThat(empSummary.getGrossEarnings()).isLessThan(BigDecimal.valueOf(80000.00));
        assertThat(empSummary.getNetSalary()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should execute complete payroll lifecycle: DRAFT -> CALCULATION -> APPROVE -> FINALIZE and generate payslips")
    void testPayrollLifecycleAndPayslipGeneration() {
        salaryStructureService.assignSalaryStructure(
                EmployeeSalaryStructureDto.builder()
                        .employeeId(testEmployee.getId())
                        .salaryStructureId(standardStructure.getId())
                        .effectiveFrom(LocalDate.of(2026, 1, 1))
                        .annualCtc(BigDecimal.valueOf(1200000.00))
                        .monthlyBaseSalary(BigDecimal.valueOf(50000.00))
                        .build(),
                adminUserId
        );

        PayrollPeriodDto period = payrollService.createPeriod(PayrollPeriodDto.builder()
                .year(2026)
                .month(1)
                .build());
        assertThat(period.getStatus()).isEqualTo("DRAFT");

        // Calculate -> REVIEW
        payrollService.calculatePayrollRun(period.getId(), adminUserId);
        PayrollPeriodDto reviewedPeriod = payrollService.getPeriod(period.getId());
        assertThat(reviewedPeriod.getStatus()).isEqualTo("REVIEW");

        // Approve -> APPROVED
        PayrollPeriodDto approvedPeriod = payrollService.approvePeriod(period.getId());
        assertThat(approvedPeriod.getStatus()).isEqualTo("APPROVED");

        // Finalize -> FINALIZED
        PayrollPeriodDto finalizedPeriod = payrollService.finalizePeriod(period.getId(), adminUserId);
        assertThat(finalizedPeriod.getStatus()).isEqualTo("FINALIZED");
        assertThat(finalizedPeriod.getFinalizedAt()).isNotNull();

        // Payslips should now be available for the employee
        List<PayslipDto> payslips = payrollService.getEmployeePayslips(testEmployee.getId());
        assertThat(payslips).hasSize(1);
        PayslipDto slip = payslips.get(0);
        assertThat(slip.getNetSalary()).isEqualByComparingTo("78000.00");
        assertThat(slip.getPaymentStatus()).isEqualTo("UNPAID");
        assertThat(slip.getPayslipNumber()).contains("2026-01");
    }
}

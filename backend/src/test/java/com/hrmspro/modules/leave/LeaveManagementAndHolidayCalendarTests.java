package com.hrmspro.modules.leave;

import com.hrmspro.common.context.TenantContext;
import com.hrmspro.modules.auth.dto.RegisterRequest;
import com.hrmspro.modules.auth.dto.UserProfileDto;
import com.hrmspro.modules.auth.service.AuthService;
import com.hrmspro.modules.employee.Employee;
import com.hrmspro.modules.employee.EmployeeRepository;
import com.hrmspro.modules.employee.EmployeeService;
import com.hrmspro.modules.employee.dto.CreateEmployeeRequest;
import com.hrmspro.modules.employee.dto.EmployeeResponseDto;
import com.hrmspro.modules.leave.dto.*;
import com.hrmspro.modules.shift.ShiftAssignmentService;
import com.hrmspro.modules.shift.ShiftService;
import com.hrmspro.modules.shift.dto.ShiftAssignmentRequest;
import com.hrmspro.modules.shift.dto.ShiftTemplateRequest;
import com.hrmspro.modules.shift.dto.ShiftTemplateResponse;
import com.hrmspro.modules.tenant.TenantService;
import com.hrmspro.modules.tenant.dto.BranchDto;
import com.hrmspro.modules.tenant.dto.CreateTenantRequest;
import com.hrmspro.modules.tenant.dto.TenantResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class LeaveManagementAndHolidayCalendarTests {

    @Autowired
    private LeaveService leaveService;

    @Autowired
    private HolidayService holidayService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private ShiftService shiftService;

    @Autowired
    private ShiftAssignmentService shiftAssignmentService;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private AuthService authService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private LeaveTypeRepository leaveTypeRepository;

    @Autowired
    private LeavePolicyRepository leavePolicyRepository;

    @Autowired
    private EmployeeLeaveBalanceRepository balanceRepository;

    @Autowired
    private LeaveLedgerRepository ledgerRepository;

    @Autowired
    private LeaveApplicationRepository applicationRepository;

    private UUID tenantId;
    private UUID adminUserId;
    private UUID managerUserId;
    private UUID employeeUserId;
    private UUID employeeId;
    private BranchDto branch;

    @BeforeEach
    void setUp() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String adminEmail = "leave.admin." + uniqueSuffix + "@company.com";
        String mgrEmail = "leave.mgr." + uniqueSuffix + "@company.com";
        String empEmail = "leave.emp." + uniqueSuffix + "@company.com";

        UserProfileDto adminUser = authService.register(RegisterRequest.builder()
                .email(adminEmail)
                .password("SecurePass123!")
                .firstName("Leave")
                .lastName("Admin")
                .build());
        adminUserId = adminUser.getId();

        TenantResponseDto tenant = tenantService.createTenant(
                adminUserId,
                CreateTenantRequest.builder()
                        .name("Zenith Global Systems")
                        .subdomain("zenith-" + uniqueSuffix)
                        .companyCode("ZEN" + uniqueSuffix)
                        .email(adminEmail)
                        .build()
        );
        tenantId = tenant.getId();
        TenantContext.setTenantId(tenantId);

        branch = tenantService.createBranch(tenantId, BranchDto.builder()
                .name("HQ Innovation Center")
                .code("HQ-" + uniqueSuffix)
                .build());

        // Register Manager
        UserProfileDto mgrUser = authService.register(RegisterRequest.builder()
                .email(mgrEmail)
                .password("SecurePass123!")
                .firstName("Michael")
                .lastName("Scott")
                .build());
        managerUserId = mgrUser.getId();

        EmployeeResponseDto mgrEmp = employeeService.createEmployee(tenantId, adminUserId, CreateEmployeeRequest.builder()
                .firstName("Michael")
                .lastName("Scott")
                .workEmail(mgrEmail)
                .joiningDate(LocalDate.now().minusYears(2))
                .designation("Regional Manager")
                .employmentType("FULL_TIME")
                .branchId(branch.getId())
                .build());

        // Register Employee
        UserProfileDto empUser = authService.register(RegisterRequest.builder()
                .email(empEmail)
                .password("SecurePass123!")
                .firstName("Jim")
                .lastName("Halpert")
                .build());
        employeeUserId = empUser.getId();

        EmployeeResponseDto empDto = employeeService.createEmployee(tenantId, adminUserId, CreateEmployeeRequest.builder()
                .firstName("Jim")
                .lastName("Halpert")
                .workEmail(empEmail)
                .joiningDate(LocalDate.now().minusYears(1))
                .designation("Sales Representative")
                .employmentType("FULL_TIME")
                .branchId(branch.getId())
                .reportingManagerId(mgrEmp.getId())
                .build());
        employeeId = empDto.getId();
    }

    @Test
    @DisplayName("1. Holiday Calendar: Create company & branch holiday, check date range")
    void testHolidayCalendarCreationAndScoping() {
        // Company-wide holiday
        HolidayDto compHoliday = holidayService.createHoliday(tenantId, HolidayDto.builder()
                .name("New Year's Day")
                .holidayDate(LocalDate.of(2026, 1, 1))
                .holidayType("PUBLIC")
                .isRecurring(true)
                .build());
        assertThat(compHoliday.getId()).isNotNull();
        assertThat(compHoliday.getBranchId()).isNull();

        // Branch-specific holiday
        HolidayDto branchHoliday = holidayService.createHoliday(tenantId, HolidayDto.builder()
                .branchId(branch.getId())
                .name("Karnataka Rajyotsava")
                .holidayDate(LocalDate.of(2026, 11, 1))
                .holidayType("REGIONAL")
                .build());
        assertThat(branchHoliday.getId()).isNotNull();
        assertThat(branchHoliday.getBranchId()).isEqualTo(branch.getId());

        // Verify retrieval for employee's branch
        List<Holiday> holidays = holidayService.getHolidaysForEmployeeDateRange(
                tenantId, branch.getId(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertThat(holidays).hasSize(2);
    }

    @Test
    @DisplayName("2. Leave Type & Policy Configuration")
    void testLeaveTypeAndPolicyConfiguration() {
        LeaveTypeDto clType = leaveService.createLeaveType(tenantId, LeaveTypeDto.builder()
                .name("Casual Leave")
                .code("CASUAL_LEAVE")
                .isPaid(true)
                .isHalfDayAllowed(true)
                .build());
        assertThat(clType.getId()).isNotNull();

        // Duplicate code rejection
        assertThrows(IllegalArgumentException.class, () ->
                leaveService.createLeaveType(tenantId, LeaveTypeDto.builder()
                        .name("Casual Leave Duplicate")
                        .code("CASUAL_LEAVE")
                        .build()));

        // Configure policy
        LeavePolicyDto policy = leaveService.configurePolicy(tenantId, LeavePolicyDto.builder()
                .leaveTypeId(clType.getId())
                .allocationDays(new BigDecimal("12.00"))
                .accrualFrequency("MONTHLY")
                .accrualRate(new BigDecimal("1.00"))
                .allowCarryForward(true)
                .maxCarryForwardDays(new BigDecimal("5.00"))
                .policyEffectiveYear(2026)
                .build());
        assertThat(policy.getId()).isNotNull();
        assertThat(policy.getAccrualRate()).isEqualByComparingTo("1.00");
    }

    @Test
    @DisplayName("3. Monthly Accrual Idempotency and Ledger Logging")
    void testAccrualIdempotencyAndLedger() {
        LeaveTypeDto slType = leaveService.createLeaveType(tenantId, LeaveTypeDto.builder()
                .name("Sick Leave")
                .code("SICK_LEAVE")
                .isPaid(true)
                .build());

        leaveService.configurePolicy(tenantId, LeavePolicyDto.builder()
                .leaveTypeId(slType.getId())
                .allocationDays(new BigDecimal("12.00"))
                .accrualFrequency("MONTHLY")
                .accrualRate(new BigDecimal("1.50"))
                .policyEffectiveYear(2026)
                .build());

        // First accrual for Jan 2026
        leaveService.executePeriodicAccrual(tenantId, 2026, 1, adminUserId);

        EmployeeLeaveBalance bal1 = leaveService.getOrCreateBalance(tenantId, employeeId, slType.getId(), 2026);
        assertThat(bal1.getAccruedAmount()).isEqualByComparingTo("1.50");
        assertThat(bal1.getAvailableBalance()).isEqualByComparingTo("1.50");

        // Idempotent re-run for same month
        leaveService.executePeriodicAccrual(tenantId, 2026, 1, adminUserId);
        EmployeeLeaveBalance bal2 = leaveService.getOrCreateBalance(tenantId, employeeId, slType.getId(), 2026);
        assertThat(bal2.getAccruedAmount()).isEqualByComparingTo("1.50"); // Did not double-accrue!

        // Accrual for Feb 2026
        leaveService.executePeriodicAccrual(tenantId, 2026, 2, adminUserId);
        EmployeeLeaveBalance bal3 = leaveService.getOrCreateBalance(tenantId, employeeId, slType.getId(), 2026);
        assertThat(bal3.getAccruedAmount()).isEqualByComparingTo("3.00");
    }

    @Test
    @DisplayName("4. Chargeable Leave Days: Excludes Weekly Offs and Holidays")
    void testChargeableDaysExcludesWeeklyOffsAndHolidays() {
        LeaveTypeDto elType = leaveService.createLeaveType(tenantId, LeaveTypeDto.builder()
                .name("Earned Leave")
                .code("EARNED_LEAVE")
                .build());

        LeavePolicyDto policyDto = leaveService.configurePolicy(tenantId, LeavePolicyDto.builder()
                .leaveTypeId(elType.getId())
                .name("EL Policy")
                .allocationDays(new BigDecimal("15.00"))
                .countWeeklyOffAsLeave(false)
                .countHolidayAsLeave(false)
                .policyEffectiveYear(2026)
                .build());

        LeavePolicy policy = leavePolicyRepository.findById(policyDto.getId()).orElseThrow();

        // Assign Monday-Friday shift with Sat+Sun off
        ShiftTemplateRequest sReq = new ShiftTemplateRequest();
        sReq.setName("Standard General");
        sReq.setCode("STD-GEN");
        sReq.setStartTime(LocalTime.of(9, 0));
        sReq.setEndTime(LocalTime.of(18, 0));
        sReq.setTimezone("Asia/Kolkata");
        sReq.setWeeklyOffDays(List.of("SATURDAY", "SUNDAY"));
        ShiftTemplateResponse shift = shiftService.createShift(tenantId, adminUserId, sReq);

        ShiftAssignmentRequest assign = new ShiftAssignmentRequest();
        assign.setEmployeeId(employeeId);
        assign.setShiftTemplateId(shift.getId());
        assign.setAssignmentType("PERMANENT");
        assign.setEffectiveStart(LocalDate.of(2026, 11, 1));
        shiftAssignmentService.assignShift(tenantId, adminUserId, assign);

        // Add a holiday on Wednesday, Nov 4, 2026
        holidayService.createHoliday(tenantId, HolidayDto.builder()
                .name("Midweek Festival")
                .holidayDate(LocalDate.of(2026, 11, 4))
                .build());

        // Range: Monday Nov 2 to Friday Nov 6 (5 calendar days, 1 holiday -> 4 chargeable days)
        Employee employee = employeeRepository.findById(employeeId).orElseThrow();
        BigDecimal days = leaveService.calculateChargeableDays(tenantId, employee, policy,
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6), false);
        assertThat(days).isEqualByComparingTo("4.00");

        // Range spanning weekend: Friday Nov 6 to Tuesday Nov 10 (5 calendar days, 2 weekend days -> 3 chargeable days)
        BigDecimal spanWeekend = leaveService.calculateChargeableDays(tenantId, employee, policy,
                LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 10), false);
        assertThat(spanWeekend).isEqualByComparingTo("3.00");
    }

    @Test
    @DisplayName("5. Leave Application, Overlap Rejection, & Balance Sufficiency")
    void testLeaveApplicationAndBalanceValidation() {
        LeaveTypeDto clType = leaveService.createLeaveType(tenantId, LeaveTypeDto.builder()
                .name("Casual Leave")
                .code("CASUAL_LEAVE")
                .build());

        leaveService.configurePolicy(tenantId, LeavePolicyDto.builder()
                .leaveTypeId(clType.getId())
                .allocationDays(new BigDecimal("12.00"))
                .accrualFrequency("MONTHLY")
                .accrualRate(new BigDecimal("2.00"))
                .policyEffectiveYear(2026)
                .build());

        // Allocate 2 days
        leaveService.executePeriodicAccrual(tenantId, 2026, 1, adminUserId);

        // Apply for 3 days -> Must fail due to insufficient balance
        ApplyLeaveRequest exceedReq = new ApplyLeaveRequest();
        exceedReq.setLeaveTypeId(clType.getId());
        exceedReq.setStartDate(LocalDate.of(2026, 1, 5));
        exceedReq.setEndDate(LocalDate.of(2026, 1, 7));
        exceedReq.setReason("Long vacation");

        assertThrows(IllegalStateException.class, () ->
                leaveService.applyLeave(tenantId, employeeUserId, exceedReq));

        // Apply for 1 day -> Succeeded
        ApplyLeaveRequest validReq = new ApplyLeaveRequest();
        validReq.setLeaveTypeId(clType.getId());
        validReq.setStartDate(LocalDate.of(2026, 1, 5));
        validReq.setEndDate(LocalDate.of(2026, 1, 5));
        validReq.setReason("Personal errand");

        LeaveApplicationDto app = leaveService.applyLeave(tenantId, employeeUserId, validReq);
        assertThat(app.getId()).isNotNull();
        assertThat(app.getStatus()).isEqualTo("PENDING");

        // Overlapping application on same date -> Must fail
        assertThrows(IllegalStateException.class, () ->
                leaveService.applyLeave(tenantId, employeeUserId, validReq));
    }

    @Test
    @DisplayName("6. Leave Approval, Self-Approval Prevention, & Ledger Debit")
    void testLeaveApprovalAndSelfApprovalPrevention() {
        LeaveTypeDto clType = leaveService.createLeaveType(tenantId, LeaveTypeDto.builder()
                .name("Casual Leave")
                .code("CASUAL_LEAVE")
                .build());

        leaveService.configurePolicy(tenantId, LeavePolicyDto.builder()
                .leaveTypeId(clType.getId())
                .accrualFrequency("MONTHLY")
                .accrualRate(new BigDecimal("5.00"))
                .policyEffectiveYear(2026)
                .build());

        leaveService.executePeriodicAccrual(tenantId, 2026, 1, adminUserId);

        ApplyLeaveRequest req = new ApplyLeaveRequest();
        req.setLeaveTypeId(clType.getId());
        req.setStartDate(LocalDate.of(2026, 1, 8));
        req.setEndDate(LocalDate.of(2026, 1, 8));
        req.setReason("Medical checkup");

        LeaveApplicationDto app = leaveService.applyLeave(tenantId, employeeUserId, req);

        // Self-approval attempt by Jim -> Must throw AccessDeniedException
        assertThrows(AccessDeniedException.class, () ->
                leaveService.approveLeave(tenantId, employeeUserId, app.getId(), "Self approved"));

        // Manager approval -> Succeeded
        LeaveApplicationDto approved = leaveService.approveLeave(tenantId, managerUserId, app.getId(), "Approved by Michael");
        assertThat(approved.getStatus()).isEqualTo("APPROVED");

        // Verify balance debit
        EmployeeLeaveBalance bal = leaveService.getOrCreateBalance(tenantId, employeeId, clType.getId(), 2026);
        assertThat(bal.getUsedAmount()).isEqualByComparingTo("1.00");
        assertThat(bal.getAvailableBalance()).isEqualByComparingTo("4.00");

        // Verify attendance lookup
        boolean onLeave = leaveService.isEmployeeOnApprovedLeave(tenantId, employeeId, LocalDate.of(2026, 1, 8));
        assertThat(onLeave).isTrue();
    }

    @Test
    @DisplayName("7. Leave Cancellation & Ledger Reversal")
    void testLeaveCancellationAndReversal() {
        LeaveTypeDto clType = leaveService.createLeaveType(tenantId, LeaveTypeDto.builder()
                .name("Casual Leave")
                .code("CASUAL_LEAVE")
                .build());

        leaveService.configurePolicy(tenantId, LeavePolicyDto.builder()
                .leaveTypeId(clType.getId())
                .accrualFrequency("MONTHLY")
                .accrualRate(new BigDecimal("5.00"))
                .policyEffectiveYear(2026)
                .build());

        leaveService.executePeriodicAccrual(tenantId, 2026, 1, adminUserId);

        ApplyLeaveRequest req = new ApplyLeaveRequest();
        req.setLeaveTypeId(clType.getId());
        req.setStartDate(LocalDate.of(2026, 1, 12));
        req.setEndDate(LocalDate.of(2026, 1, 12));
        req.setReason("Family function");

        LeaveApplicationDto app = leaveService.applyLeave(tenantId, employeeUserId, req);
        leaveService.approveLeave(tenantId, managerUserId, app.getId(), "Approved");

        // Cancel approved leave
        LeaveApplicationDto cancelled = leaveService.cancelLeave(tenantId, employeeUserId, app.getId());
        assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");

        // Verify balance restored
        EmployeeLeaveBalance bal = leaveService.getOrCreateBalance(tenantId, employeeId, clType.getId(), 2026);
        assertThat(bal.getUsedAmount()).isEqualByComparingTo("0.00");
        assertThat(bal.getAvailableBalance()).isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("8. Half-Day Leave Application & Reporting")
    void testHalfDayLeaveAndReporting() {
        LeaveTypeDto clType = leaveService.createLeaveType(tenantId, LeaveTypeDto.builder()
                .name("Casual Leave")
                .code("CASUAL_LEAVE")
                .isHalfDayAllowed(true)
                .build());

        leaveService.configurePolicy(tenantId, LeavePolicyDto.builder()
                .leaveTypeId(clType.getId())
                .accrualFrequency("MONTHLY")
                .accrualRate(new BigDecimal("5.00"))
                .policyEffectiveYear(2026)
                .build());

        leaveService.executePeriodicAccrual(tenantId, 2026, 1, adminUserId);

        ApplyLeaveRequest req = new ApplyLeaveRequest();
        req.setLeaveTypeId(clType.getId());
        req.setStartDate(LocalDate.of(2026, 1, 15));
        req.setEndDate(LocalDate.of(2026, 1, 15));
        req.setHalfDay(true);
        req.setHalfDaySession("FIRST_HALF");
        req.setReason("Dentist appointment");

        LeaveApplicationDto app = leaveService.applyLeave(tenantId, employeeUserId, req);
        assertThat(app.getTotalDays()).isEqualByComparingTo("0.50");

        leaveService.approveLeave(tenantId, managerUserId, app.getId(), "Approved half-day");

        // Check report
        var report = leaveService.getLeaveReport(tenantId, employeeId, null, null, null, "APPROVED",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), org.springframework.data.domain.Pageable.unpaged());
        assertThat(report.getContent()).hasSize(1);
        assertThat(report.getContent().get(0).getTotalDays()).isEqualByComparingTo("0.50");
    }
}

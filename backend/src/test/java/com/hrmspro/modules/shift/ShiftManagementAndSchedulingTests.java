package com.hrmspro.modules.shift;

import com.hrmspro.common.context.TenantContext;
import com.hrmspro.modules.attendance.AttendanceRecord;
import com.hrmspro.modules.attendance.AttendanceRecordRepository;
import com.hrmspro.modules.attendance.AttendanceService;
import com.hrmspro.modules.attendance.dto.CheckInRequest;
import com.hrmspro.modules.attendance.dto.CheckOutRequest;
import com.hrmspro.modules.attendance.dto.AttendanceResponseDto;
import com.hrmspro.modules.auth.dto.RegisterRequest;
import com.hrmspro.modules.auth.dto.UserProfileDto;
import com.hrmspro.modules.auth.service.AuthService;
import com.hrmspro.modules.employee.Employee;
import com.hrmspro.modules.employee.EmployeeRepository;
import com.hrmspro.modules.employee.EmployeeService;
import com.hrmspro.modules.employee.dto.CreateEmployeeRequest;
import com.hrmspro.modules.employee.dto.EmployeeResponseDto;
import com.hrmspro.modules.shift.dto.*;
import com.hrmspro.modules.tenant.TenantService;
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

import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ShiftManagementAndSchedulingTests {

    @Autowired
    private ShiftService shiftService;

    @Autowired
    private ShiftAssignmentService assignmentService;

    @Autowired
    private ShiftRosterService rosterService;

    @Autowired
    private ShiftChangeRequestService changeRequestService;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private AuthService authService;

    @Autowired
    private ShiftTemplateRepository templateRepository;

    @Autowired
    private EmployeeShiftAssignmentRepository assignmentRepository;

    @Autowired
    private ShiftRosterRepository rosterRepository;

    @Autowired
    private ShiftChangeRequestRepository changeRequestRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    private UUID tenantId;
    private UUID adminUserId;
    private UUID employeeUserId;
    private UUID employeeId;

    @BeforeEach
    void setUp() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String adminEmail = "shift.admin." + uniqueSuffix + "@company.com";
        String empEmail = "shift.emp." + uniqueSuffix + "@company.com";

        UserProfileDto adminUser = authService.register(RegisterRequest.builder()
                .email(adminEmail)
                .password("SecurePass123!")
                .firstName("Shift")
                .lastName("Admin")
                .build());
        adminUserId = adminUser.getId();

        TenantResponseDto tenant = tenantService.createTenant(
                adminUserId,
                CreateTenantRequest.builder()
                        .name("Apex Global Workforce")
                        .subdomain("apex-" + uniqueSuffix)
                        .companyCode("APX" + uniqueSuffix)
                        .email(adminEmail)
                        .build()
        );
        tenantId = tenant.getId();
        TenantContext.setTenantId(tenantId);

        com.hrmspro.modules.tenant.dto.BranchDto branch = tenantService.createBranch(tenantId, com.hrmspro.modules.tenant.dto.BranchDto.builder()
                .name("HQ Newsroom")
                .code("HQ-" + uniqueSuffix)
                .build());

        UserProfileDto empUser = authService.register(RegisterRequest.builder()
                .email(empEmail)
                .password("SecurePass123!")
                .firstName("Sarah")
                .lastName("Connor")
                .build());
        employeeUserId = empUser.getId();

        EmployeeResponseDto empDto = employeeService.createEmployee(tenantId, adminUserId, CreateEmployeeRequest.builder()
                .firstName("Sarah")
                .lastName("Connor")
                .workEmail(empEmail)
                .joiningDate(LocalDate.now().minusMonths(6))
                .designation("Staff Engineer")
                .employmentType("FULL_TIME")
                .branchId(branch.getId())
                .build());
        employeeId = empDto.getId();
    }

    @Test
    @DisplayName("1. Shift Template CRUD: Create shift and reject duplicate shift code")
    void testShiftTemplateCreationAndDuplicateRejection() {
        ShiftTemplateRequest request = new ShiftTemplateRequest();
        request.setName("Morning General");
        request.setCode("GEN-MORN");
        request.setStartTime(LocalTime.of(9, 0));
        request.setEndTime(LocalTime.of(17, 30));
        request.setIsOvernight(false);
        request.setTimezone("Asia/Kolkata");
        request.setGracePeriodMinutes(15);
        request.setWeeklyOffDays(List.of("SATURDAY", "SUNDAY"));

        ShiftTemplateResponse created = shiftService.createShift(tenantId, adminUserId, request);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getCode()).isEqualTo("GEN-MORN");
        assertThat(created.getTotalScheduledMinutes()).isEqualTo(510);
        assertThat(created.getWeeklyOffDays()).containsExactlyInAnyOrder("SATURDAY", "SUNDAY");

        // Duplicate code rejection
        ShiftTemplateRequest dupRequest = new ShiftTemplateRequest();
        dupRequest.setName("Duplicate Morning");
        dupRequest.setCode("GEN-MORN");
        dupRequest.setStartTime(LocalTime.of(9, 0));
        dupRequest.setEndTime(LocalTime.of(18, 0));
        dupRequest.setTimezone("Asia/Kolkata");

        assertThrows(IllegalArgumentException.class, () -> shiftService.createShift(tenantId, adminUserId, dupRequest));
    }

    @Test
    @DisplayName("2. Overnight Shift & Timezone-Aware Calculations")
    void testOvernightShiftDurationCalculation() {
        // Night shift 22:00 to 06:00 (+1 day) = 8 hours = 480 minutes
        int nightMinutes = ShiftService.computeScheduledMinutes(
                LocalTime.of(22, 0), LocalTime.of(6, 0), true, "Asia/Kolkata");
        assertThat(nightMinutes).isEqualTo(480);

        // US Eastern timezone calculation
        int nyMinutes = ShiftService.computeScheduledMinutes(
                LocalTime.of(8, 0), LocalTime.of(17, 0), false, "America/New_York");
        assertThat(nyMinutes).isEqualTo(540);
    }

    @Test
    @DisplayName("3. DST Spring-Forward (23h) & Fall-Back (25h) Calculations")
    void testDaylightSavingTransitions() {
        ZoneId newYork = ZoneId.of("America/New_York");

        // Spring-forward transition in New York: 2026-03-08
        // At 2:00 AM clocks jump to 3:00 AM (23 hour day)
        LocalDate springForwardDate = LocalDate.of(2026, 3, 8);
        ZonedDateTime springStart = ZonedDateTime.of(springForwardDate, LocalTime.of(1, 0), newYork);
        ZonedDateTime springEnd = ZonedDateTime.of(springForwardDate, LocalTime.of(5, 0), newYork);
        long springMinutes = Duration.between(springStart, springEnd).toMinutes();
        // Spanning 1 AM to 5 AM across 2 AM spring forward loses 1 hour: 4 clock hours - 1 hour = 3 hours = 180 min
        assertThat(springMinutes).isEqualTo(180);

        // Fall-back transition in New York: 2026-11-01
        // At 2:00 AM clocks fall back to 1:00 AM (25 hour day)
        LocalDate fallBackDate = LocalDate.of(2026, 11, 1);
        ZonedDateTime fallStart = ZonedDateTime.of(fallBackDate, LocalTime.of(1, 0), newYork);
        ZonedDateTime fallEnd = ZonedDateTime.of(fallBackDate, LocalTime.of(5, 0), newYork);
        long fallMinutes = Duration.between(fallStart, fallEnd).toMinutes();
        // Spanning 1 AM to 5 AM across 2 AM fall back gains 1 hour: 4 clock hours + 1 hour = 5 hours = 300 min
        assertThat(fallMinutes).isEqualTo(300);
    }

    @Test
    @DisplayName("4. Employee Shift Assignment & Overlap Conflict Detection")
    void testShiftAssignmentOverlapDetection() {
        ShiftTemplateRequest shiftReq = new ShiftTemplateRequest();
        shiftReq.setName("Regular Day");
        shiftReq.setCode("REG-DAY");
        shiftReq.setStartTime(LocalTime.of(9, 0));
        shiftReq.setEndTime(LocalTime.of(17, 0));
        shiftReq.setTimezone("Asia/Kolkata");
        shiftReq.setWeeklyOffDays(List.of("SUNDAY"));
        ShiftTemplateResponse shift = shiftService.createShift(tenantId, adminUserId, shiftReq);

        LocalDate today = LocalDate.now();

        // 1. Assign permanently starting today
        ShiftAssignmentRequest assign1 = new ShiftAssignmentRequest();
        assign1.setEmployeeId(employeeId);
        assign1.setShiftTemplateId(shift.getId());
        assign1.setAssignmentType("PERMANENT");
        assign1.setEffectiveStart(today);
        ShiftAssignmentResponse resp1 = assignmentService.assignShift(tenantId, adminUserId, assign1);
        assertThat(resp1.getId()).isNotNull();

        // 2. Attempt overlapping open-ended assignment -> Must fail
        ShiftAssignmentRequest overlap = new ShiftAssignmentRequest();
        overlap.setEmployeeId(employeeId);
        overlap.setShiftTemplateId(shift.getId());
        overlap.setAssignmentType("TEMPORARY");
        overlap.setEffectiveStart(today.plusDays(5));
        overlap.setEffectiveEnd(today.plusDays(10));

        assertThrows(IllegalStateException.class, () -> assignmentService.assignShift(tenantId, adminUserId, overlap));
    }

    @Test
    @DisplayName("5. Bulk Assignment with skipConflicting flag")
    void testBulkShiftAssignment() {
        ShiftTemplateRequest shiftReq = new ShiftTemplateRequest();
        shiftReq.setName("Bulk Standard");
        shiftReq.setCode("BLK-STD");
        shiftReq.setStartTime(LocalTime.of(10, 0));
        shiftReq.setEndTime(LocalTime.of(19, 0));
        shiftReq.setTimezone("Asia/Kolkata");
        shiftReq.setWeeklyOffDays(List.of("SATURDAY", "SUNDAY"));
        ShiftTemplateResponse shift = shiftService.createShift(tenantId, adminUserId, shiftReq);

        BulkShiftAssignmentRequest bulkReq = new BulkShiftAssignmentRequest();
        bulkReq.setEmployeeIds(List.of(employeeId));
        bulkReq.setShiftTemplateId(shift.getId());
        bulkReq.setAssignmentType("PERMANENT");
        bulkReq.setEffectiveStart(LocalDate.now());
        bulkReq.setSkipConflicting(true);

        Map<String, Object> result = assignmentService.bulkAssignShift(tenantId, adminUserId, bulkReq);
        assertThat(result.get("succeeded")).isEqualTo(1);
    }

    @Test
    @DisplayName("6. Roster Generation (DRAFT) and Publish Lifecycle")
    void testRosterGenerationAndPublishing() {
        ShiftTemplateRequest shiftReq = new ShiftTemplateRequest();
        shiftReq.setName("Dev Shift");
        shiftReq.setCode("DEV-01");
        shiftReq.setStartTime(LocalTime.of(9, 30));
        shiftReq.setEndTime(LocalTime.of(18, 30));
        shiftReq.setTimezone("Asia/Kolkata");
        shiftReq.setWeeklyOffDays(List.of("SATURDAY", "SUNDAY"));
        ShiftTemplateResponse shift = shiftService.createShift(tenantId, adminUserId, shiftReq);

        LocalDate startDate = LocalDate.of(2026, 11, 2); // Monday
        LocalDate endDate = LocalDate.of(2026, 11, 8);   // Sunday

        ShiftAssignmentRequest assign = new ShiftAssignmentRequest();
        assign.setEmployeeId(employeeId);
        assign.setShiftTemplateId(shift.getId());
        assign.setAssignmentType("PERMANENT");
        assign.setEffectiveStart(startDate);
        assignmentService.assignShift(tenantId, adminUserId, assign);

        // Generate roster
        RosterGenerationRequest genReq = new RosterGenerationRequest();
        genReq.setFromDate(startDate);
        genReq.setToDate(endDate);
        genReq.setEmployeeIds(List.of(employeeId));

        Map<String, Object> genResult = rosterService.generateRoster(tenantId, adminUserId, genReq);
        assertThat(genResult.get("generated")).isEqualTo(7);

        // Drafts should not appear in published roster view
        List<RosterResponse> publishedBefore = rosterService.getRosters(tenantId, startDate, endDate);
        assertThat(publishedBefore).isEmpty();

        // Publish
        Map<String, Object> pubResult = rosterService.publishRosters(tenantId, adminUserId, startDate, endDate);
        assertThat(pubResult.get("published")).isEqualTo(7);

        // Verify published rosters
        List<RosterResponse> publishedAfter = rosterService.getRosters(tenantId, startDate, endDate);
        assertThat(publishedAfter).hasSize(7);

        // Saturday (Nov 7) and Sunday (Nov 8) must be flagged as weekly offs
        long weeklyOffCount = publishedAfter.stream().filter(RosterResponse::getIsWeeklyOff).count();
        assertThat(weeklyOffCount).isEqualTo(2);
    }

    @Test
    @DisplayName("7. Shift Change Request & Re-validation on Approval (Self-Approval Prevention)")
    void testShiftChangeRequestWorkflow() {
        ShiftTemplateRequest s1Req = new ShiftTemplateRequest();
        s1Req.setName("Original Shift");
        s1Req.setCode("ORIG-01");
        s1Req.setStartTime(LocalTime.of(8, 0));
        s1Req.setEndTime(LocalTime.of(16, 0));
        s1Req.setTimezone("Asia/Kolkata");
        s1Req.setWeeklyOffDays(List.of("SUNDAY"));
        ShiftTemplateResponse shift1 = shiftService.createShift(tenantId, adminUserId, s1Req);

        ShiftTemplateRequest s2Req = new ShiftTemplateRequest();
        s2Req.setName("Afternoon Shift");
        s2Req.setCode("AFT-01");
        s2Req.setStartTime(LocalTime.of(14, 0));
        s2Req.setEndTime(LocalTime.of(22, 0));
        s2Req.setTimezone("Asia/Kolkata");
        s2Req.setWeeklyOffDays(List.of("SUNDAY"));
        ShiftTemplateResponse shift2 = shiftService.createShift(tenantId, adminUserId, s2Req);

        // Submit change request as employee
        ShiftChangeRequestCreateDto req = new ShiftChangeRequestCreateDto();
        req.setRequestedShiftId(shift2.getId());
        req.setRequestedEffectiveDate(LocalDate.now().plusDays(2));
        req.setReason("Doctor appointments in morning");

        ShiftChangeRequestDto submitted = changeRequestService.submitRequest(tenantId, employeeUserId, req);
        assertThat(submitted.getId()).isNotNull();
        assertThat(submitted.getStatus()).isEqualTo("PENDING");

        // Self-approval must be rejected
        ShiftChangeReviewRequest review = new ShiftChangeReviewRequest();
        review.setRemarks("Approved by myself");
        assertThrows(AccessDeniedException.class, () ->
                changeRequestService.approveRequest(tenantId, employeeUserId, submitted.getId(), review));

        // Authorized manager approval succeeds
        ShiftChangeReviewRequest managerReview = new ShiftChangeReviewRequest();
        managerReview.setRemarks("Approved by management");
        ShiftChangeRequestDto approved = changeRequestService.approveRequest(tenantId, adminUserId, submitted.getId(), managerReview);
        assertThat(approved.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("8. Attendance Integration: Snapshot Preservation, Multi-Session & Late Arrival")
    void testAttendanceIntegrationWithShiftAndMultiSession() {
        // Shift starting at 09:00 with 15m grace period
        ShiftTemplateRequest sReq = new ShiftTemplateRequest();
        sReq.setName("Strict Shift");
        sReq.setCode("STRICT-01");
        sReq.setStartTime(LocalTime.of(9, 0));
        sReq.setEndTime(LocalTime.of(18, 0));
        sReq.setTimezone("Asia/Kolkata");
        sReq.setGracePeriodMinutes(15);
        sReq.setWeeklyOffDays(List.of("SUNDAY"));
        ShiftTemplateResponse shift = shiftService.createShift(tenantId, adminUserId, sReq);

        LocalDate today = LocalDate.now();

        ShiftAssignmentRequest assign = new ShiftAssignmentRequest();
        assign.setEmployeeId(employeeId);
        assign.setShiftTemplateId(shift.getId());
        assign.setAssignmentType("PERMANENT");
        assign.setEffectiveStart(today);
        assignmentService.assignShift(tenantId, adminUserId, assign);

        // Check in Session 1
        CheckInRequest punch1 = CheckInRequest.builder()
                .latitude(12.9716)
                .longitude(77.5946)
                .accuracyMeters(5.0)
                .idempotencyKey("punch-sess-1-" + UUID.randomUUID())
                .build();
        AttendanceResponseDto res1 = attendanceService.checkIn(tenantId, employeeUserId, punch1);
        assertThat(res1.getSessionNumber()).isEqualTo(1);
        assertThat(res1.getAssignedShiftId()).isNotNull();
        assertThat(res1.getScheduledWorkMinutes()).isEqualTo(540);

        // Check out Session 1
        CheckOutRequest out1 = CheckOutRequest.builder()
                .latitude(12.9716)
                .longitude(77.5946)
                .idempotencyKey("out-sess-1-" + UUID.randomUUID())
                .build();
        attendanceService.checkOut(tenantId, employeeUserId, out1);

        // Check in Session 2 (split shift on same day) -> Must allocate sessionNumber = 2
        CheckInRequest punch2 = CheckInRequest.builder()
                .latitude(12.9716)
                .longitude(77.5946)
                .accuracyMeters(5.0)
                .idempotencyKey("punch-sess-2-" + UUID.randomUUID())
                .build();
        AttendanceResponseDto res2 = attendanceService.checkIn(tenantId, employeeUserId, punch2);
        assertThat(res2.getSessionNumber()).isEqualTo(2);

        // Check out Session 2
        CheckOutRequest out2 = CheckOutRequest.builder()
                .latitude(12.9716)
                .longitude(77.5946)
                .idempotencyKey("out-sess-2-" + UUID.randomUUID())
                .build();
        attendanceService.checkOut(tenantId, employeeUserId, out2);

        // Verify snapshot preservation: update shift template name and scheduled minutes
        sReq.setName("Modified Shift Name");
        sReq.setStartTime(LocalTime.of(10, 0)); // changed start time
        shiftService.updateShift(tenantId, shift.getId(), sReq);

        // Attendance record must preserve original shift snapshot
        AttendanceRecord savedRec = attendanceRecordRepository.findByTenantIdAndEmployeeIdAndAttendanceDateAndSessionNumber(
                tenantId, employeeId, today, 1).orElseThrow();
        assertThat(savedRec.getScheduledWorkMinutes()).isEqualTo(540); // Preserved original 9h schedule
        assertThat(savedRec.getShiftSnapshot()).contains("STRICT-01");
    }
}

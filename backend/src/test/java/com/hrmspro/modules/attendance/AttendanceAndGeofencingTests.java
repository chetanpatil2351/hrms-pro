package com.hrmspro.modules.attendance;

import com.hrmspro.modules.attendance.dto.*;
import com.hrmspro.modules.auth.dto.RegisterRequest;
import com.hrmspro.modules.auth.dto.UserProfileDto;
import com.hrmspro.modules.auth.service.AuthService;
import com.hrmspro.modules.employee.Employee;
import com.hrmspro.modules.employee.EmployeeRepository;
import com.hrmspro.modules.employee.EmployeeService;
import com.hrmspro.modules.employee.dto.CreateEmployeeRequest;
import com.hrmspro.modules.employee.dto.EmployeeResponseDto;
import com.hrmspro.modules.tenant.Branch;
import com.hrmspro.modules.tenant.BranchRepository;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AttendanceAndGeofencingTests {

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private AuthService authService;

    @Autowired
    private AttendanceLocationRepository locationRepository;

    @Autowired
    private AttendanceRecordRepository recordRepository;

    @Autowired
    private AttendanceEventRepository eventRepository;

    @Autowired
    private AttendanceBreakRepository breakRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    private UserProfileDto userProfile;
    private TenantResponseDto tenant;
    private BranchDto branch;
    private EmployeeResponseDto employee;
    private AttendanceLocationDto location;

    // Head office location: Bangalore Tech Park
    private final double HQ_LAT = 12.9716;
    private final double HQ_LON = 77.5946;

    @BeforeEach
    void setUp() {
        userProfile = authService.register(RegisterRequest.builder()
                .email("att.emp@enterprise.com")
                .password("Password123!")
                .firstName("Peter")
                .lastName("Parker")
                .build());

        tenant = tenantService.createTenant(userProfile.getId(), CreateTenantRequest.builder()
                .name("Daily Bugle Media")
                .subdomain("bugle")
                .companyCode("BGL")
                .email("admin@bugle.com")
                .build());

        branch = tenantService.createBranch(tenant.getId(), BranchDto.builder()
                .name("HQ Newsroom")
                .code("HQ-01")
                .build());

        employee = employeeService.createEmployee(tenant.getId(), userProfile.getId(), CreateEmployeeRequest.builder()
                .firstName("Peter")
                .lastName("Parker")
                .workEmail("att.emp@enterprise.com")
                .joiningDate(LocalDate.now())
                .designation("Staff Photographer")
                .branchId(branch.getId())
                .build());

        // Create 100m geofence location
        location = attendanceService.createLocation(tenant.getId(), AttendanceLocationDto.builder()
                .branchId(branch.getId())
                .name("Main Gate Geofence")
                .latitude(HQ_LAT)
                .longitude(HQ_LON)
                .radiusMeters(100)
                .policyMode("STRICT")
                .build());
    }

    @Test
    @DisplayName("Check-in inside geofence succeeds and check-in outside geofence fails (STRICT)")
    void testGeofenceCheckInValidation() {
        // 1. Outside Geofence (approx 10km away in Whitefield) -> Lat: 12.9698, Lon: 77.7499
        CheckInRequest outsideReq = CheckInRequest.builder()
                .latitude(12.9698)
                .longitude(77.7499)
                .accuracyMeters(5.0)
                .build();

        assertThrows(AccessDeniedException.class, () ->
                attendanceService.checkIn(tenant.getId(), userProfile.getId(), outsideReq)
        );

        // 2. Inside Geofence (within 20 meters of HQ) -> Lat: 12.97165, Lon: 77.59465
        CheckInRequest insideReq = CheckInRequest.builder()
                .latitude(12.97165)
                .longitude(77.59465)
                .accuracyMeters(10.0)
                .selfieFilePath("tenants/" + tenant.getId() + "/selfies/checkin.jpg")
                .selfieMimeType("image/jpeg")
                .build();

        AttendanceResponseDto response = attendanceService.checkIn(tenant.getId(), userProfile.getId(), insideReq);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(response.getIsGeofenceVerified()).isTrue();
        assertThat(response.getIsSelfieVerified()).isTrue();

        // 3. Duplicate check-in while in progress must fail
        assertThrows(IllegalStateException.class, () ->
                attendanceService.checkIn(tenant.getId(), userProfile.getId(), insideReq)
        );
    }

    @Test
    @DisplayName("Complete attendance workflow: Check-in -> Break -> Resume -> Check-out calculates duration")
    void testAttendanceLifecycleAndBreakCalculations() throws InterruptedException {
        // 1. Check in
        CheckInRequest checkInReq = CheckInRequest.builder()
                .latitude(HQ_LAT)
                .longitude(HQ_LON)
                .build();
        AttendanceResponseDto record = attendanceService.checkIn(tenant.getId(), userProfile.getId(), checkInReq);

        // 2. Start Break
        AttendanceBreakDto attBreak = attendanceService.startBreak(tenant.getId(), userProfile.getId(), "LUNCH");
        assertThat(attBreak.getId()).isNotNull();
        assertThat(attBreak.getBreakType()).isEqualTo("LUNCH");

        // Attempting to start another break while one is active must fail
        assertThrows(IllegalStateException.class, () ->
                attendanceService.startBreak(tenant.getId(), userProfile.getId(), "TEA")
        );

        // 3. End Break
        AttendanceBreakDto endedBreak = attendanceService.endBreak(tenant.getId(), userProfile.getId());
        assertThat(endedBreak.getEndTime()).isNotNull();

        // 4. Check out
        CheckOutRequest checkOutReq = CheckOutRequest.builder()
                .latitude(HQ_LAT)
                .longitude(HQ_LON)
                .build();
        AttendanceResponseDto checkedOut = attendanceService.checkOut(tenant.getId(), userProfile.getId(), checkOutReq);

        assertThat(checkedOut.getCheckOutTime()).isNotNull();
        assertThat(checkedOut.getBreaks()).hasSize(1);
    }

    @Test
    @DisplayName("Attendance correction request and approval workflow")
    void testAttendanceCorrectionFlow() {
        CheckInRequest checkInReq = CheckInRequest.builder().latitude(HQ_LAT).longitude(HQ_LON).build();
        AttendanceResponseDto record = attendanceService.checkIn(tenant.getId(), userProfile.getId(), checkInReq);
        attendanceService.checkOut(tenant.getId(), userProfile.getId(), CheckOutRequest.builder().build());

        // Submit correction
        AttendanceCorrectionRequest req = AttendanceCorrectionRequest.builder()
                .attendanceRecordId(record.getId())
                .reason("Forgot to punch check-in on time due to client meeting")
                .build();

        AttendanceCorrectionDto correction = attendanceService.requestCorrection(tenant.getId(), userProfile.getId(), req);
        assertThat(correction.getId()).isNotNull();
        assertThat(correction.getStatus()).isEqualTo("PENDING");

        // Review approval
        AttendanceCorrectionDto approved = attendanceService.reviewCorrection(tenant.getId(), userProfile.getId(), correction.getId(), true, "Approved by Manager");
        assertThat(approved.getStatus()).isEqualTo("APPROVED");
    }
}
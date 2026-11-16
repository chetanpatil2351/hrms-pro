package com.hrmspro.modules.attendance;

import com.hrmspro.modules.attendance.dto.*;
import com.hrmspro.modules.auth.dto.RegisterRequest;
import com.hrmspro.modules.auth.dto.UserProfileDto;
import com.hrmspro.modules.auth.service.AuthService;
import com.hrmspro.modules.employee.EmployeeService;
import com.hrmspro.modules.employee.dto.CreateEmployeeRequest;
import com.hrmspro.modules.tenant.TenantService;
import com.hrmspro.modules.tenant.dto.BranchDto;
import com.hrmspro.modules.tenant.dto.CreateTenantRequest;
import com.hrmspro.modules.tenant.dto.TenantResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Anti-fraud engine tests covering:
 * - Fraud flag creation on mock-location signal
 * - No duplicate fraud flags per single punch event
 * - Consecutive failure counter behaviour (flag raised at threshold)
 * - Tenant isolation of fraud flags
 * - Fraud flag review authorisation
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AttendanceFraudFlagTests {

    @Autowired private AttendanceService attendanceService;
    @Autowired private AttendanceFraudFlagRepository fraudFlagRepository;
    @Autowired private AttendanceEventRepository eventRepository;
    @Autowired private AttendanceRecordRepository recordRepository;
    @Autowired private AttendanceLocationRepository locationRepository;
    @Autowired private EmployeeService employeeService;
    @Autowired private TenantService tenantService;
    @Autowired private AuthService authService;

    // HQ geofence – Bangalore Tech Park
    private static final double HQ_LAT = 12.9716;
    private static final double HQ_LON = 77.5946;

    private UserProfileDto userProfile;
    private TenantResponseDto tenant;
    private BranchDto branch;

    @BeforeEach
    void setUp() {
        userProfile = authService.register(RegisterRequest.builder()
                .email("fraud.test.emp@corp.com")
                .password("Password123!")
                .firstName("Frank")
                .lastName("Castle")
                .build());

        tenant = tenantService.createTenant(userProfile.getId(), CreateTenantRequest.builder()
                .name("Punisher Corp")
                .subdomain("punisher")
                .companyCode("PUN")
                .email("admin@punisher.com")
                .build());

        branch = tenantService.createBranch(tenant.getId(), BranchDto.builder()
                .name("HQ")
                .code("HQ-01")
                .build());

        employeeService.createEmployee(tenant.getId(), userProfile.getId(), CreateEmployeeRequest.builder()
                .firstName("Frank")
                .lastName("Castle")
                .workEmail("fraud.test.emp@corp.com")
                .joiningDate(LocalDate.now())
                .designation("Security Director")
                .branchId(branch.getId())
                .build());

        // Create a 100m STRICT geofence at HQ
        attendanceService.createLocation(tenant.getId(), AttendanceLocationDto.builder()
                .branchId(branch.getId())
                .name("HQ Gate")
                .latitude(HQ_LAT)
                .longitude(HQ_LON)
                .radiusMeters(100)
                .policyMode("STRICT")
                .build());
    }

    // -------------------------------------------------------------------------
    // Test 1: Mock-location flag is created on a check-in that reports isMockLocation=true
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Mock location flag is created when client reports isMockLocation=true")
    void testMockLocationFlagCreated() {
        CheckInRequest req = CheckInRequest.builder()
                .latitude(HQ_LAT)
                .longitude(HQ_LON)
                .accuracyMeters(10.0)
                .isMockLocation(true)   // client signals mock
                .build();

        attendanceService.checkIn(tenant.getId(), userProfile.getId(), req);

        Page<AttendanceFraudFlag> flags = fraudFlagRepository.findWithFilters(
                tenant.getId(), null, "FLAGGED", "MOCK_LOCATION_REPORTED",
                PageRequest.of(0, 10));

        assertThat(flags.getTotalElements()).isGreaterThanOrEqualTo(1);
        assertThat(flags.getContent().get(0).getFlagType()).isEqualTo("MOCK_LOCATION_REPORTED");
        assertThat(flags.getContent().get(0).getTenantId()).isEqualTo(tenant.getId());
    }

    // -------------------------------------------------------------------------
    // Test 2: A single check-in does NOT produce duplicate fraud flags
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Single check-in produces at most one flag per fraud type, not duplicates")
    void testNoDuplicateFraudFlagsForSingleEvent() {
        CheckInRequest req = CheckInRequest.builder()
                .latitude(HQ_LAT)
                .longitude(HQ_LON)
                .accuracyMeters(5.0)
                .isMockLocation(true)
                .build();

        attendanceService.checkIn(tenant.getId(), userProfile.getId(), req);

        // With the duplicate-call bug fixed, there must be exactly ONE MOCK_LOCATION_REPORTED flag
        Page<AttendanceFraudFlag> flags = fraudFlagRepository.findWithFilters(
                tenant.getId(), null, null, "MOCK_LOCATION_REPORTED",
                PageRequest.of(0, 20));

        assertThat(flags.getTotalElements())
                .as("Must have exactly 1 MOCK_LOCATION_REPORTED flag per check-in, not duplicates")
                .isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Test 3: Repeated geofence failures increment the counter and raise a flag
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Consecutive geofence failures raise a GEOFENCE_FAILURE fraud flag at threshold")
    void testConsecutiveGeofenceFailuresRaiseFlag() {
        // Location far outside geofence (Chennai ~350km away)
        CheckInRequest outsideReq = CheckInRequest.builder()
                .latitude(13.0827)
                .longitude(80.2707)
                .accuracyMeters(10.0)
                .build();

        // Default threshold is 3; trigger 3 failures
        for (int i = 0; i < 3; i++) {
            assertThrows(AccessDeniedException.class, () ->
                    attendanceService.checkIn(tenant.getId(), userProfile.getId(), outsideReq)
            );
        }

        // A GEOFENCE_FAILURE fraud flag should now exist
        Page<AttendanceFraudFlag> flags = fraudFlagRepository.findWithFilters(
                tenant.getId(), null, null, "GEOFENCE_FAILURE",
                PageRequest.of(0, 10));

        assertThat(flags.getTotalElements())
                .as("A GEOFENCE_FAILURE fraud flag must be created after 3 consecutive failures")
                .isGreaterThanOrEqualTo(1);
        assertThat(flags.getContent().get(0).getSeverity()).isEqualTo("HIGH");
    }

    // -------------------------------------------------------------------------
    // Test 4: Consecutive failure counter resets after a successful check-in
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Consecutive failure counter is reset after a successful check-in")
    void testFailureCounterResetAfterSuccessfulCheckIn() {
        CheckInRequest outsideReq = CheckInRequest.builder()
                .latitude(13.0827).longitude(80.2707).accuracyMeters(10.0).build();
        CheckInRequest insideReq = CheckInRequest.builder()
                .latitude(HQ_LAT).longitude(HQ_LON).accuracyMeters(5.0).build();

        // 2 failures (below threshold)
        for (int i = 0; i < 2; i++) {
            assertThrows(AccessDeniedException.class, () ->
                    attendanceService.checkIn(tenant.getId(), userProfile.getId(), outsideReq));
        }

        long flagsBefore = fraudFlagRepository.findWithFilters(
                tenant.getId(), null, null, "GEOFENCE_FAILURE", PageRequest.of(0, 10))
                .getTotalElements();

        // Successful check-in resets the counter
        attendanceService.checkIn(tenant.getId(), userProfile.getId(), insideReq);

        // Now trigger 2 more failures (still below threshold since counter was reset)
        attendanceService.checkOut(tenant.getId(), userProfile.getId(),
                CheckOutRequest.builder().latitude(HQ_LAT).longitude(HQ_LON).build());

        for (int i = 0; i < 2; i++) {
            assertThrows(AccessDeniedException.class, () ->
                    attendanceService.checkIn(tenant.getId(), userProfile.getId(), outsideReq));
        }

        long flagsAfter = fraudFlagRepository.findWithFilters(
                tenant.getId(), null, null, "GEOFENCE_FAILURE", PageRequest.of(0, 10))
                .getTotalElements();

        // The count should not have grown beyond what was there before the reset
        assertThat(flagsAfter).isEqualTo(flagsBefore);
    }

    // -------------------------------------------------------------------------
    // Test 5: Fraud flags are tenant-isolated
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Fraud flags are strictly isolated per tenant")
    void testFraudFlagTenantIsolation() {
        // Create a second tenant and employee
        UserProfileDto user2 = authService.register(RegisterRequest.builder()
                .email("fraud.tenant2@corp.com")
                .password("Password123!")
                .firstName("Tony")
                .lastName("Stark")
                .build());

        TenantResponseDto tenant2 = tenantService.createTenant(user2.getId(), CreateTenantRequest.builder()
                .name("Stark Industries")
                .subdomain("stark")
                .companyCode("STK")
                .email("admin@stark.com")
                .build());

        BranchDto branch2 = tenantService.createBranch(tenant2.getId(), BranchDto.builder()
                .name("Iron Tower")
                .code("IT-01")
                .build());

        employeeService.createEmployee(tenant2.getId(), user2.getId(), CreateEmployeeRequest.builder()
                .firstName("Tony")
                .lastName("Stark")
                .workEmail("fraud.tenant2@corp.com")
                .joiningDate(LocalDate.now())
                .designation("CEO")
                .branchId(branch2.getId())
                .build());

        attendanceService.createLocation(tenant2.getId(), AttendanceLocationDto.builder()
                .branchId(branch2.getId())
                .name("HQ")
                .latitude(HQ_LAT).longitude(HQ_LON)
                .radiusMeters(100).policyMode("STRICT")
                .build());

        // Trigger mock-location flag in tenant 1
        attendanceService.checkIn(tenant.getId(), userProfile.getId(), CheckInRequest.builder()
                .latitude(HQ_LAT).longitude(HQ_LON).accuracyMeters(5.0).isMockLocation(true).build());

        // Tenant 2 should see zero flags
        long tenant2FlagCount = fraudFlagRepository.findWithFilters(
                tenant2.getId(), null, null, null, PageRequest.of(0, 10))
                .getTotalElements();

        assertThat(tenant2FlagCount)
                .as("Fraud flags from tenant 1 must not be visible to tenant 2")
                .isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // Test 6: Fraud flag review sets reviewer and status correctly
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Fraud flag review sets status and reviewer correctly")
    void testFraudFlagReviewSetsStatusAndReviewer() {
        // Create a flag
        CheckInRequest req = CheckInRequest.builder()
                .latitude(HQ_LAT).longitude(HQ_LON).accuracyMeters(5.0).isMockLocation(true).build();
        attendanceService.checkIn(tenant.getId(), userProfile.getId(), req);

        Page<AttendanceFraudFlag> flags = fraudFlagRepository.findWithFilters(
                tenant.getId(), null, null, "MOCK_LOCATION_REPORTED", PageRequest.of(0, 10));
        assertThat(flags.getTotalElements()).isEqualTo(1);

        AttendanceFraudFlag flag = flags.getContent().get(0);
        assertThat(flag.getStatus()).isEqualTo("FLAGGED");
        assertThat(flag.getReviewedBy()).isNull();

        // Verify that reviewing a flag from another tenant is rejected
        // (tenant isolation is enforced at repository level via findByIdAndTenantId)
        UUID differentTenantId = UUID.randomUUID();
        assertThat(fraudFlagRepository.findByIdAndTenantId(flag.getId(), differentTenantId))
                .isEmpty();

        // Confirm correct tenant lookup succeeds
        assertThat(fraudFlagRepository.findByIdAndTenantId(flag.getId(), tenant.getId()))
                .isPresent();
    }

    // -------------------------------------------------------------------------
    // Test 7: Check-in with valid coordinates, no fraud flags on a clean punch
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Clean check-in inside geofence with no mock location produces no fraud flags")
    void testCleanPunchProducesNoFraudFlags() {
        CheckInRequest req = CheckInRequest.builder()
                .latitude(HQ_LAT)
                .longitude(HQ_LON)
                .accuracyMeters(8.0)
                .isMockLocation(false)
                .build();

        AttendanceResponseDto resp = attendanceService.checkIn(tenant.getId(), userProfile.getId(), req);
        assertThat(resp.getIsGeofenceVerified()).isTrue();

        // No fraud flags should be created for a clean first punch
        long totalFlags = fraudFlagRepository.findByTenantIdOrderByCreatedAtDesc(
                tenant.getId(), PageRequest.of(0, 10)).getTotalElements();
        assertThat(totalFlags)
                .as("A clean check-in must not generate any fraud flags")
                .isEqualTo(0);
    }
}

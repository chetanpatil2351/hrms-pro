package com.hrmspro.modules.attendance;

import com.hrmspro.common.exception.ResourceNotFoundException;
import com.hrmspro.modules.attendance.dto.*;
import com.hrmspro.modules.employee.Employee;
import com.hrmspro.modules.employee.EmployeeRepository;
import com.hrmspro.modules.tenant.Branch;
import com.hrmspro.modules.tenant.BranchRepository;
import com.hrmspro.modules.tenant.Tenant;
import com.hrmspro.modules.tenant.TenantRepository;
import com.hrmspro.modules.user.AppUser;
import com.hrmspro.modules.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRecordRepository recordRepository;
    private final AttendanceEventRepository eventRepository;
    private final AttendanceLocationRepository locationRepository;
    private final AttendanceBreakRepository breakRepository;
    private final AttendanceCorrectionRepository correctionRepository;
    private final EmployeeRepository employeeRepository;
    private final BranchRepository branchRepository;
    private final TenantRepository tenantRepository;
    private final AppUserRepository userRepository;
    private final com.hrmspro.modules.shift.ShiftAssignmentService shiftAssignmentService;
    private final com.hrmspro.modules.shift.ShiftService shiftService;

    // --- Geofence Location Management ---

    @Transactional
    public AttendanceLocationDto createLocation(UUID tenantId, AttendanceLocationDto request) {
        Branch branch = branchRepository.findById(request.getBranchId())
                .filter(b -> b.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Branch does not belong to this company"));

        AttendanceLocation location = AttendanceLocation.builder()
                .branch(branch)
                .name(request.getName().trim())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .radiusMeters(request.getRadiusMeters() != null ? request.getRadiusMeters() : 100)
                .policyMode(request.getPolicyMode() != null ? request.getPolicyMode().toUpperCase() : "STRICT")
                .isActive(request.getActive() != null ? request.getActive() : true)
                .build();
        location.setTenantId(tenantId);
        location = locationRepository.save(location);

        return mapToLocationDto(location);
    }

    @Transactional(readOnly = true)
    public List<AttendanceLocationDto> getLocations(UUID tenantId, UUID branchId) {
        List<AttendanceLocation> list = branchId != null ?
                locationRepository.findByTenantIdAndBranchIdAndIsActiveTrue(tenantId, branchId) :
                locationRepository.findByTenantIdAndIsActiveTrue(tenantId);
        return list.stream().map(this::mapToLocationDto).collect(Collectors.toList());
    }

    // --- Check-In & Check-Out Engine ---

    @Transactional
    public AttendanceResponseDto checkIn(UUID tenantId, UUID userId, CheckInRequest request) {
        Employee employee = getEmployeeForUser(tenantId, userId);

        // 1. Idempotency Check
        if (request.getIdempotencyKey() != null) {
            Optional<AttendanceEvent> existingEvent = eventRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existingEvent.isPresent()) {
                return mapToResponseDto(existingEvent.get().getAttendanceRecord());
            }
        }

        // 2. Prevent active duplicate check-in
        Optional<AttendanceRecord> inProgressRecord = recordRepository
                .findFirstByTenantIdAndEmployeeIdAndStatusOrderByCheckInTimeDesc(tenantId, employee.getId(), "IN_PROGRESS");
        if (inProgressRecord.isPresent()) {
            throw new IllegalStateException("An active check-in session is already in progress for this employee");
        }

        Instant serverNow = Instant.now();
        ZoneId zoneId = getTenantZoneId(tenantId);
        LocalDate attendanceDate = serverNow.atZone(zoneId).toLocalDate();

        // Safe Session Number Allocation
        Integer maxSession = recordRepository.findMaxSessionNumber(tenantId, employee.getId(), attendanceDate);
        int sessionNumber = (maxSession != null ? maxSession : 0) + 1;

        // 3. Geofence Distance Validation
        GeofenceEvaluation geofence = evaluateGeofence(tenantId, employee.getBranch(), request.getLatitude(), request.getLongitude(), request.getLocationId());

        if (geofence.failed && "STRICT".equalsIgnoreCase(geofence.policyMode)) {
            throw new AccessDeniedException("Check-in rejected: Location is outside authorized geofence (" +
                    String.format("%.1f", geofence.distanceMeters) + "m away, limit " + geofence.radiusMeters + "m)");
        }

        // 4. Shift Integration & Late Arrival Evaluation
        UUID assignedShiftId = null;
        String shiftSnapshot = null;
        Instant scheduledStartUtc = null;
        Instant scheduledEndUtc = null;
        Integer scheduledWorkMinutes = null;
        boolean isLate = false;
        int lateByMinutes = 0;

        Optional<com.hrmspro.modules.shift.EmployeeShiftAssignment> optAssignment =
                shiftAssignmentService.resolveEffectiveAssignment(employee.getId(), attendanceDate);

        if (optAssignment.isPresent()) {
            com.hrmspro.modules.shift.EmployeeShiftAssignment assignment = optAssignment.get();
            com.hrmspro.modules.shift.ShiftTemplate template = assignment.getShiftTemplate();
            assignedShiftId = assignment.getId();
            shiftSnapshot = shiftService.buildShiftSnapshot(template);
            scheduledWorkMinutes = template.getTotalScheduledMinutes();

            ZoneId shiftZoneId = ZoneId.of(template.getTimezone());
            scheduledStartUtc = com.hrmspro.modules.shift.ShiftService.computeScheduledStart(
                    attendanceDate, template.getStartTime(), shiftZoneId);
            scheduledEndUtc = com.hrmspro.modules.shift.ShiftService.computeScheduledEnd(
                    attendanceDate, template.getEndTime(), template.getIsOvernight(), shiftZoneId);

            int graceMinutes = template.getGracePeriodMinutes() != null ? template.getGracePeriodMinutes() : 0;
            Instant graceDeadline = scheduledStartUtc.plus(Duration.ofMinutes(graceMinutes));

            if (serverNow.isAfter(graceDeadline)) {
                isLate = true;
                lateByMinutes = (int) Duration.between(scheduledStartUtc, serverNow).toMinutes();
            }
        }

        // 5. Create Master Record
        AttendanceRecord record = AttendanceRecord.builder()
                .employee(employee)
                .branch(employee.getBranch())
                .attendanceDate(attendanceDate)
                .sessionNumber(sessionNumber)
                .assignedShiftId(assignedShiftId)
                .shiftSnapshot(shiftSnapshot)
                .scheduledStartUtc(scheduledStartUtc)
                .scheduledEndUtc(scheduledEndUtc)
                .scheduledWorkMinutes(scheduledWorkMinutes)
                .isLate(isLate)
                .lateByMinutes(lateByMinutes)
                .checkInTime(serverNow)
                .status("IN_PROGRESS")
                .isGeofenceVerified(!geofence.failed)
                .isSelfieVerified(request.getSelfieFilePath() != null && !request.getSelfieFilePath().isBlank())
                .build();
        record.setTenantId(tenantId);
        record = recordRepository.save(record);

        // 5. Create Raw Event
        AttendanceEvent event = AttendanceEvent.builder()
                .attendanceRecord(record)
                .employee(employee)
                .eventType("CHECK_IN")
                .idempotencyKey(request.getIdempotencyKey())
                .eventTime(serverNow)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .accuracyMeters(request.getAccuracyMeters())
                .distanceMeters(geofence.distanceMeters)
                .geofenceLocation(geofence.location)
                .geofenceStatus(geofence.status)
                .isMockLocation(Boolean.TRUE.equals(request.getIsMockLocation()))
                .selfieFilePath(request.getSelfieFilePath())
                .selfieMimeType(request.getSelfieMimeType())
                .selfieVerified(record.getIsSelfieVerified())
                .deviceInfo(request.getDeviceInfo())
                .ipAddress(request.getIpAddress())
                .build();
        event.setTenantId(tenantId);
        eventRepository.save(event);

        return mapToResponseDto(record);
    }

    @Transactional
    public AttendanceResponseDto checkOut(UUID tenantId, UUID userId, CheckOutRequest request) {
        Employee employee = getEmployeeForUser(tenantId, userId);

        if (request.getIdempotencyKey() != null) {
            Optional<AttendanceEvent> existingEvent = eventRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existingEvent.isPresent()) {
                return mapToResponseDto(existingEvent.get().getAttendanceRecord());
            }
        }

        // 1. Find active in-progress record
        AttendanceRecord record = recordRepository
                .findFirstByTenantIdAndEmployeeIdAndStatusOrderByCheckInTimeDesc(tenantId, employee.getId(), "IN_PROGRESS")
                .orElseThrow(() -> new IllegalStateException("No active check-in session found to check out from"));

        // 2. End any open breaks
        breakRepository.findFirstByAttendanceRecordIdAndEndTimeIsNull(record.getId()).ifPresent(activeBreak -> {
            activeBreak.setEndTime(Instant.now());
            activeBreak.setDurationMinutes((int) Duration.between(activeBreak.getStartTime(), activeBreak.getEndTime()).toMinutes());
            breakRepository.save(activeBreak);
        });

        Instant serverNow = Instant.now();

        // 3. Geofence Check
        GeofenceEvaluation geofence = evaluateGeofence(tenantId, record.getBranch(), request.getLatitude(), request.getLongitude(), request.getLocationId());

        // 4. Calculations
        int totalWorkMinutes = (int) Duration.between(record.getCheckInTime(), serverNow).toMinutes();
        int totalBreakMinutes = breakRepository.findByAttendanceRecordIdOrderByStartTimeAsc(record.getId()).stream()
                .mapToInt(b -> b.getDurationMinutes() != null ? b.getDurationMinutes() : 0)
                .sum();
        int netWorkMinutes = Math.max(0, totalWorkMinutes - totalBreakMinutes);

        // Early departure calculation if scheduledEndUtc is present
        boolean isEarlyDeparture = false;
        int earlyDepartureByMinutes = 0;
        if (record.getScheduledEndUtc() != null && serverNow.isBefore(record.getScheduledEndUtc())) {
            isEarlyDeparture = true;
            earlyDepartureByMinutes = (int) Duration.between(serverNow, record.getScheduledEndUtc()).toMinutes();
        }

        record.setCheckOutTime(serverNow);
        record.setWorkDurationMinutes(totalWorkMinutes);
        record.setBreakDurationMinutes(totalBreakMinutes);
        record.setNetWorkDurationMinutes(netWorkMinutes);
        record.setIsEarlyDeparture(isEarlyDeparture);
        record.setEarlyDepartureByMinutes(earlyDepartureByMinutes);
        record.setStatus(netWorkMinutes >= 480 ? "PRESENT" : (netWorkMinutes >= 240 ? "HALF_DAY" : "INCOMPLETE"));
        record = recordRepository.save(record);

        // 5. Create Event
        AttendanceEvent event = AttendanceEvent.builder()
                .attendanceRecord(record)
                .employee(employee)
                .eventType("CHECK_OUT")
                .idempotencyKey(request.getIdempotencyKey())
                .eventTime(serverNow)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .accuracyMeters(request.getAccuracyMeters())
                .distanceMeters(geofence.distanceMeters)
                .geofenceLocation(geofence.location)
                .geofenceStatus(geofence.status)
                .isMockLocation(Boolean.TRUE.equals(request.getIsMockLocation()))
                .selfieFilePath(request.getSelfieFilePath())
                .selfieMimeType(request.getSelfieMimeType())
                .selfieVerified(request.getSelfieFilePath() != null && !request.getSelfieFilePath().isBlank())
                .deviceInfo(request.getDeviceInfo())
                .ipAddress(request.getIpAddress())
                .build();
        event.setTenantId(tenantId);
        eventRepository.save(event);

        return mapToResponseDto(record);
    }

    // --- Break Management ---

    @Transactional
    public AttendanceBreakDto startBreak(UUID tenantId, UUID userId, String breakType) {
        Employee employee = getEmployeeForUser(tenantId, userId);
        AttendanceRecord record = recordRepository
                .findFirstByTenantIdAndEmployeeIdAndStatusOrderByCheckInTimeDesc(tenantId, employee.getId(), "IN_PROGRESS")
                .orElseThrow(() -> new IllegalStateException("Must be checked-in to start a break"));

        if (breakRepository.findFirstByAttendanceRecordIdAndEndTimeIsNull(record.getId()).isPresent()) {
            throw new IllegalStateException("A break is already active");
        }

        AttendanceBreak attBreak = AttendanceBreak.builder()
                .attendanceRecord(record)
                .employee(employee)
                .breakType(breakType != null ? breakType.toUpperCase() : "GENERAL")
                .startTime(Instant.now())
                .build();
        attBreak.setTenantId(tenantId);
        attBreak = breakRepository.save(attBreak);

        return mapToBreakDto(attBreak);
    }

    @Transactional
    public AttendanceBreakDto endBreak(UUID tenantId, UUID userId) {
        Employee employee = getEmployeeForUser(tenantId, userId);
        AttendanceRecord record = recordRepository
                .findFirstByTenantIdAndEmployeeIdAndStatusOrderByCheckInTimeDesc(tenantId, employee.getId(), "IN_PROGRESS")
                .orElseThrow(() -> new IllegalStateException("No active attendance record found"));

        AttendanceBreak activeBreak = breakRepository.findFirstByAttendanceRecordIdAndEndTimeIsNull(record.getId())
                .orElseThrow(() -> new IllegalStateException("No active break found to end"));

        Instant now = Instant.now();
        activeBreak.setEndTime(now);
        activeBreak.setDurationMinutes((int) Duration.between(activeBreak.getStartTime(), now).toMinutes());
        activeBreak = breakRepository.save(activeBreak);

        return mapToBreakDto(activeBreak);
    }

    @Transactional(readOnly = true)
    public List<AttendanceBreakDto> getMyBreaks(UUID tenantId, UUID userId) {
        Employee employee = getEmployeeForUser(tenantId, userId);
        Optional<AttendanceRecord> record = recordRepository
                .findFirstByTenantIdAndEmployeeIdAndStatusOrderByCheckInTimeDesc(tenantId, employee.getId(), "IN_PROGRESS");
        if (record.isEmpty()) return Collections.emptyList();
        return breakRepository.findByAttendanceRecordIdOrderByStartTimeAsc(record.get().getId()).stream()
                .map(this::mapToBreakDto)
                .collect(Collectors.toList());
    }

    // --- History & Queries ---

    @Transactional(readOnly = true)
    public AttendanceResponseDto getMyTodayAttendance(UUID tenantId, UUID userId) {
        Employee employee = getEmployeeForUser(tenantId, userId);
        LocalDate today = Instant.now().atZone(getTenantZoneId(tenantId)).toLocalDate();
        return recordRepository.findByTenantIdAndEmployeeIdAndAttendanceDate(tenantId, employee.getId(), today)
                .map(this::mapToResponseDto)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Page<AttendanceResponseDto> getMyAttendanceHistory(UUID tenantId, UUID userId, Pageable pageable) {
        Employee employee = getEmployeeForUser(tenantId, userId);
        return recordRepository.findByTenantIdAndEmployeeIdOrderByAttendanceDateDesc(tenantId, employee.getId(), pageable)
                .map(this::mapToResponseDto);
    }

    @Transactional(readOnly = true)
    public AttendanceResponseDto getAttendanceRecordById(UUID tenantId, UUID id) {
        AttendanceRecord record = recordRepository.findById(id)
                .filter(r -> r.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Attendance record not found"));
        return mapToResponseDto(record);
    }

    // --- Correction Requests ---

    @Transactional
    public AttendanceCorrectionDto requestCorrection(UUID tenantId, UUID userId, AttendanceCorrectionRequest request) {
        Employee employee = getEmployeeForUser(tenantId, userId);
        AttendanceRecord record = recordRepository.findById(request.getAttendanceRecordId())
                .filter(r -> r.getTenantId().equals(tenantId) && r.getEmployee().getId().equals(employee.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Attendance record not found"));

        AttendanceCorrection correction = AttendanceCorrection.builder()
                .attendanceRecord(record)
                .employee(employee)
                .requestedCheckIn(request.getRequestedCheckIn())
                .requestedCheckOut(request.getRequestedCheckOut())
                .reason(request.getReason().trim())
                .status("PENDING")
                .build();
        correction.setTenantId(tenantId);
        correction = correctionRepository.save(correction);

        return mapToCorrectionDto(correction);
    }

    @Transactional
    public AttendanceCorrectionDto reviewCorrection(UUID tenantId, UUID reviewerUserId, UUID correctionId, boolean approved, String remarks) {
        AttendanceCorrection correction = correctionRepository.findById(correctionId)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Correction request not found"));

        AppUser reviewer = userRepository.findById(reviewerUserId).orElse(null);

        correction.setStatus(approved ? "APPROVED" : "REJECTED");
        correction.setReviewedBy(reviewer);
        correction.setReviewedAt(Instant.now());
        correction.setReviewRemarks(remarks);
        correction = correctionRepository.save(correction);

        if (approved) {
            AttendanceRecord record = correction.getAttendanceRecord();
            if (correction.getRequestedCheckIn() != null) record.setCheckInTime(correction.getRequestedCheckIn());
            if (correction.getRequestedCheckOut() != null) record.setCheckOutTime(correction.getRequestedCheckOut());

            if (record.getCheckInTime() != null && record.getCheckOutTime() != null) {
                int totalMins = (int) Duration.between(record.getCheckInTime(), record.getCheckOutTime()).toMinutes();
                record.setWorkDurationMinutes(totalMins);
                record.setNetWorkDurationMinutes(Math.max(0, totalMins - record.getBreakDurationMinutes()));
                record.setStatus(record.getNetWorkDurationMinutes() >= 480 ? "PRESENT" : "HALF_DAY");
            }
            recordRepository.save(record);
        }

        return mapToCorrectionDto(correction);
    }

    // --- Geofence Haversine Calculation ---

    public static double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Radius of the Earth in meters
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c; // in meters
    }

    private static class GeofenceEvaluation {
        AttendanceLocation location;
        Double distanceMeters;
        Integer radiusMeters;
        String policyMode = "STRICT";
        String status = "VERIFIED";
        boolean failed = false;
    }

    private GeofenceEvaluation evaluateGeofence(UUID tenantId, Branch branch, Double lat, Double lon, UUID locationId) {
        GeofenceEvaluation eval = new GeofenceEvaluation();
        if (lat == null || lon == null) {
            eval.status = "NOT_APPLICABLE";
            return eval;
        }

        List<AttendanceLocation> candidateLocations;
        if (locationId != null) {
            candidateLocations = locationRepository.findById(locationId)
                    .filter(l -> l.getTenantId().equals(tenantId) && l.getIsActive())
                    .map(List::of).orElse(Collections.emptyList());
        } else if (branch != null) {
            candidateLocations = locationRepository.findByTenantIdAndBranchIdAndIsActiveTrue(tenantId, branch.getId());
        } else {
            candidateLocations = locationRepository.findByTenantIdAndIsActiveTrue(tenantId);
        }

        if (candidateLocations.isEmpty()) {
            eval.status = "VERIFIED"; // No geofence defined for this branch; allow
            return eval;
        }

        // Find nearest location
        AttendanceLocation nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (AttendanceLocation loc : candidateLocations) {
            double dist = calculateHaversineDistance(lat, lon, loc.getLatitude(), loc.getLongitude());
            if (dist < minDistance) {
                minDistance = dist;
                nearest = loc;
            }
        }

        eval.location = nearest;
        eval.distanceMeters = minDistance;
        eval.radiusMeters = nearest != null ? nearest.getRadiusMeters() : 100;
        eval.policyMode = nearest != null ? nearest.getPolicyMode() : "STRICT";

        if (minDistance <= eval.radiusMeters) {
            eval.status = "VERIFIED";
            eval.failed = false;
        } else {
            eval.failed = true;
            eval.status = "OUT_OF_BOUNDS";
        }
        return eval;
    }

    private Employee getEmployeeForUser(UUID tenantId, UUID userId) {
        return employeeRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> new AccessDeniedException("No employee profile found for the active user session in this company"));
    }

    private ZoneId getTenantZoneId(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .map(Tenant::getTimezone)
                .map(tz -> {
                    try { return ZoneId.of(tz); } catch (Exception e) { return ZoneId.of("Asia/Kolkata"); }
                })
                .orElse(ZoneId.of("Asia/Kolkata"));
    }

    private AttendanceResponseDto mapToResponseDto(AttendanceRecord r) {
        List<AttendanceBreakDto> breaks = breakRepository.findByAttendanceRecordIdOrderByStartTimeAsc(r.getId()).stream()
                .map(this::mapToBreakDto)
                .collect(Collectors.toList());

        String empName = r.getEmployee() != null ?
                (r.getEmployee().getFirstName() + " " + r.getEmployee().getLastName()).trim() : null;

        return AttendanceResponseDto.builder()
                .id(r.getId())
                .employeeId(r.getEmployee().getId())
                .employeeName(empName)
                .employeeCode(r.getEmployee().getEmployeeCode())
                .branchId(r.getBranch() != null ? r.getBranch().getId() : null)
                .branchName(r.getBranch() != null ? r.getBranch().getName() : null)
                .attendanceDate(r.getAttendanceDate())
                .checkInTime(r.getCheckInTime())
                .checkOutTime(r.getCheckOutTime())
                .status(r.getStatus())
                .workDurationMinutes(r.getWorkDurationMinutes())
                .breakDurationMinutes(r.getBreakDurationMinutes())
                .netWorkDurationMinutes(r.getNetWorkDurationMinutes())
                .isLate(r.getIsLate())
                .isEarlyDeparture(r.getIsEarlyDeparture())
                .isGeofenceVerified(r.getIsGeofenceVerified())
                .isSelfieVerified(r.getIsSelfieVerified())
                .sessionNumber(r.getSessionNumber())
                .assignedShiftId(r.getAssignedShiftId())
                .scheduledWorkMinutes(r.getScheduledWorkMinutes())
                .lateByMinutes(r.getLateByMinutes())
                .earlyDepartureByMinutes(r.getEarlyDepartureByMinutes())
                .breaks(breaks)
                .build();
    }

    private AttendanceBreakDto mapToBreakDto(AttendanceBreak b) {
        return AttendanceBreakDto.builder()
                .id(b.getId())
                .breakType(b.getBreakType())
                .startTime(b.getStartTime())
                .endTime(b.getEndTime())
                .durationMinutes(b.getDurationMinutes())
                .build();
    }

    private AttendanceLocationDto mapToLocationDto(AttendanceLocation l) {
        return AttendanceLocationDto.builder()
                .id(l.getId())
                .branchId(l.getBranch().getId())
                .branchName(l.getBranch().getName())
                .name(l.getName())
                .latitude(l.getLatitude())
                .longitude(l.getLongitude())
                .radiusMeters(l.getRadiusMeters())
                .policyMode(l.getPolicyMode())
                .active(l.getIsActive())
                .build();
    }

    private AttendanceCorrectionDto mapToCorrectionDto(AttendanceCorrection c) {
        return AttendanceCorrectionDto.builder()
                .id(c.getId())
                .attendanceRecordId(c.getAttendanceRecord().getId())
                .employeeId(c.getEmployee().getId())
                .employeeName(c.getEmployee().getFirstName() + " " + c.getEmployee().getLastName())
                .requestedCheckIn(c.getRequestedCheckIn())
                .requestedCheckOut(c.getRequestedCheckOut())
                .reason(c.getReason())
                .status(c.getStatus())
                .reviewRemarks(c.getReviewRemarks())
                .reviewedAt(c.getReviewedAt())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
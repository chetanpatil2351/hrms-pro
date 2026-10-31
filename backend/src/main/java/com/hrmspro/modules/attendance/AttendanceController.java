package com.hrmspro.modules.attendance;

import com.hrmspro.common.response.ApiResponse;
import com.hrmspro.config.security.UserPrincipal;
import com.hrmspro.modules.attendance.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
@Tag(name = "Attendance & Geofencing", description = "Endpoints for employee check-in, check-out, geofencing, breaks, and corrections")
public class AttendanceController {

    private final AttendanceService attendanceService;

    // --- Geofence Location Endpoints ---

    @PostMapping("/locations")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER')")
    @Operation(summary = "Create an authorized geofence attendance location for a branch")
    public ResponseEntity<ApiResponse<AttendanceLocationDto>> createLocation(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AttendanceLocationDto request
    ) {
        AttendanceLocationDto response = attendanceService.createLocation(principal.getTenantId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Attendance geofence location created"));
    }

    @GetMapping("/locations")
    @Operation(summary = "List authorized geofence locations")
    public ResponseEntity<ApiResponse<List<AttendanceLocationDto>>> getLocations(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID branchId
    ) {
        List<AttendanceLocationDto> response = attendanceService.getLocations(principal.getTenantId(), branchId);
        return ResponseEntity.ok(ApiResponse.success(response, "Locations retrieved"));
    }

    // --- Check-In & Check-Out Endpoints ---

    @PostMapping("/check-in")
    @Operation(summary = "Employee Check-In with Geofence & Selfie verification")
    public ResponseEntity<ApiResponse<AttendanceResponseDto>> checkIn(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody CheckInRequest request
    ) {
        AttendanceResponseDto response = attendanceService.checkIn(principal.getTenantId(), principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(response, "Check-in successful"));
    }

    @PostMapping("/check-out")
    @Operation(summary = "Employee Check-Out with Geofence & work calculation")
    public ResponseEntity<ApiResponse<AttendanceResponseDto>> checkOut(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody CheckOutRequest request
    ) {
        AttendanceResponseDto response = attendanceService.checkOut(principal.getTenantId(), principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(response, "Check-out successful"));
    }

    @GetMapping("/me/today")
    @Operation(summary = "Get current authenticated user's attendance status for today")
    public ResponseEntity<ApiResponse<AttendanceResponseDto>> getMyTodayAttendance(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        AttendanceResponseDto response = attendanceService.getMyTodayAttendance(principal.getTenantId(), principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response, "Today's attendance retrieved"));
    }

    @GetMapping("/me/history")
    @Operation(summary = "Get current authenticated user's attendance history")
    public ResponseEntity<ApiResponse<Page<AttendanceResponseDto>>> getMyAttendanceHistory(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20, sort = "attendanceDate", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<AttendanceResponseDto> response = attendanceService.getMyAttendanceHistory(principal.getTenantId(), principal.getUserId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(response, "Attendance history retrieved"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER') or hasRole('ATTENDANCE_MANAGER') or hasRole('BRANCH_MANAGER')")
    @Operation(summary = "Get specific attendance record by ID")
    public ResponseEntity<ApiResponse<AttendanceResponseDto>> getAttendanceRecordById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        AttendanceResponseDto response = attendanceService.getAttendanceRecordById(principal.getTenantId(), id);
        return ResponseEntity.ok(ApiResponse.success(response, "Attendance record retrieved"));
    }

    // --- Break Management Endpoints ---

    @PostMapping("/breaks/start")
    @Operation(summary = "Start an attendance break (Lunch, Tea, Bio, General)")
    public ResponseEntity<ApiResponse<AttendanceBreakDto>> startBreak(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false, defaultValue = "GENERAL") String breakType
    ) {
        AttendanceBreakDto response = attendanceService.startBreak(principal.getTenantId(), principal.getUserId(), breakType);
        return ResponseEntity.ok(ApiResponse.success(response, "Break started"));
    }

    @PostMapping("/breaks/end")
    @Operation(summary = "End the currently active attendance break")
    public ResponseEntity<ApiResponse<AttendanceBreakDto>> endBreak(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        AttendanceBreakDto response = attendanceService.endBreak(principal.getTenantId(), principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response, "Break ended"));
    }

    @GetMapping("/me/breaks")
    @Operation(summary = "List breaks taken in the current active attendance session")
    public ResponseEntity<ApiResponse<List<AttendanceBreakDto>>> getMyBreaks(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<AttendanceBreakDto> response = attendanceService.getMyBreaks(principal.getTenantId(), principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response, "Breaks retrieved"));
    }

    // --- Attendance Correction Requests ---

    @PostMapping("/corrections")
    @Operation(summary = "Submit an attendance correction request")
    public ResponseEntity<ApiResponse<AttendanceCorrectionDto>> requestCorrection(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AttendanceCorrectionRequest request
    ) {
        AttendanceCorrectionDto response = attendanceService.requestCorrection(principal.getTenantId(), principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Attendance correction requested"));
    }

    @PostMapping("/corrections/{id}/review")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER') or hasRole('ATTENDANCE_MANAGER')")
    @Operation(summary = "Review and approve/reject an attendance correction request")
    public ResponseEntity<ApiResponse<AttendanceCorrectionDto>> reviewCorrection(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam boolean approved,
            @RequestParam(required = false) String remarks
    ) {
        AttendanceCorrectionDto response = attendanceService.reviewCorrection(principal.getTenantId(), principal.getUserId(), id, approved, remarks);
        return ResponseEntity.ok(ApiResponse.success(response, "Correction reviewed successfully"));
    }
}
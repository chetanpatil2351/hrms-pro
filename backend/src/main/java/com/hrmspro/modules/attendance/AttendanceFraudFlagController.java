package com.hrmspro.modules.attendance;

import com.hrmspro.common.response.ApiResponse;
import com.hrmspro.config.security.UserPrincipal;
import com.hrmspro.config.security.UserPrincipalHelper;
import com.hrmspro.modules.user.AppUser;
import com.hrmspro.modules.attendance.dto.AttendanceFraudFlagDto;
import com.hrmspro.modules.attendance.dto.ReviewFraudFlagRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attendance/fraud-flags")
@RequiredArgsConstructor
public class AttendanceFraudFlagController {

    private final AttendanceFraudFlagRepository fraudFlagRepository;
    private final UserPrincipalHelper principalHelper;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('COMPANY_ADMIN','HR_MANAGER','ATTENDANCE_MANAGER')")
    public ResponseEntity<ApiResponse<Page<AttendanceFraudFlagDto>>> listFraudFlags(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String flagType) {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UUID tenantId = principal.getTenantId();
        Pageable pageable = PageRequest.of(page, size);
        Page<AttendanceFraudFlag> flags = fraudFlagRepository.findWithFilters(tenantId, employeeId, status, flagType, pageable);
        Page<AttendanceFraudFlagDto> dtoPage = flags.map(this::toDto);
        return ResponseEntity.ok(ApiResponse.success(dtoPage));
    }

    @PutMapping("/{id}/review")
    @PreAuthorize("hasAnyAuthority('COMPANY_ADMIN','HR_MANAGER','ATTENDANCE_MANAGER')")
    public ResponseEntity<ApiResponse<AttendanceFraudFlagDto>> reviewFraudFlag(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewFraudFlagRequest request) {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UUID tenantId = principal.getTenantId();
        AttendanceFraudFlag flag = fraudFlagRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Fraud flag not found"));
        // Set reviewer from principal via helper
        AppUser reviewer = principalHelper.getAppUser(principal);
        flag.setReviewedBy(reviewer);
        flag.setReviewedAt(Instant.now());
        if (request.getStatus() != null) {
            flag.setStatus(request.getStatus());
        }
        if (request.getRemarks() != null) {
            flag.setReviewRemarks(request.getRemarks());
        }
        AttendanceFraudFlag saved = fraudFlagRepository.save(flag);
        return ResponseEntity.ok(ApiResponse.success(toDto(saved)));
    }

    private AttendanceFraudFlagDto toDto(AttendanceFraudFlag flag) {
        return AttendanceFraudFlagDto.builder()
                .id(flag.getId())
                .employeeId(flag.getEmployee().getId())
                .employeeName(flag.getEmployee().getFirstName() + " " + flag.getEmployee().getLastName())
                .employeeCode(flag.getEmployee().getEmployeeCode())
                .attendanceRecordId(flag.getAttendanceRecord() != null ? flag.getAttendanceRecord().getId() : null)
                .attendanceEventId(flag.getAttendanceEvent() != null ? flag.getAttendanceEvent().getId() : null)
                .flagType(flag.getFlagType())
                .severity(flag.getSeverity())
                .reason(flag.getReason())
                .details(flag.getDetails())
                .status(flag.getStatus())
                .reviewedById(flag.getReviewedBy() != null ? flag.getReviewedBy().getId() : null)
                .reviewedByName(flag.getReviewedBy() != null ? flag.getReviewedBy().getFirstName() + " " + flag.getReviewedBy().getLastName() : null)
                .reviewedAt(flag.getReviewedAt())
                .reviewRemarks(flag.getReviewRemarks())
                .createdAt(flag.getCreatedAt())
                .build();
    }
}

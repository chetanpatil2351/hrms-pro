package com.hrmspro.modules.shift;

import com.hrmspro.common.exception.ResourceNotFoundException;
import com.hrmspro.modules.employee.Employee;
import com.hrmspro.modules.employee.EmployeeRepository;
import com.hrmspro.modules.shift.dto.*;
import com.hrmspro.modules.user.AppUser;
import com.hrmspro.modules.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftChangeRequestService {

    private final ShiftChangeRequestRepository changeRequestRepository;
    private final EmployeeShiftAssignmentRepository assignmentRepository;
    private final ShiftTemplateRepository shiftTemplateRepository;
    private final EmployeeRepository employeeRepository;
    private final AppUserRepository userRepository;
    private final ShiftAssignmentService assignmentService;

    // =====================================================================
    // Submit Change Request
    // =====================================================================

    @Transactional
    public ShiftChangeRequestDto submitRequest(UUID tenantId, UUID userId, ShiftChangeRequestCreateDto request) {
        // Resolve employee from user identity
        Employee employee = employeeRepository.findByUserIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee profile not found for this user"));
        AppUser requestedBy = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ShiftTemplate requestedShift = shiftTemplateRepository.findByIdAndTenantId(request.getRequestedShiftId(), tenantId)
                .filter(ShiftTemplate::getIsActive)
                .orElseThrow(() -> new IllegalArgumentException("Requested shift template not found or inactive"));

        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new IllegalArgumentException("Reason is required for shift change requests");
        }

        // Find current assignment (if any)
        EmployeeShiftAssignment currentAssignment = assignmentService.resolveEffectiveAssignment(
                employee.getId(), request.getRequestedEffectiveDate()).orElse(null);

        ShiftChangeRequest changeRequest = ShiftChangeRequest.builder()
                .employee(employee)
                .requestedBy(requestedBy)
                .currentAssignment(currentAssignment)
                .requestedShift(requestedShift)
                .requestedEffectiveDate(request.getRequestedEffectiveDate())
                .requestedEffectiveEnd(request.getRequestedEffectiveEnd())
                .reason(request.getReason())
                .status("PENDING")
                .build();
        changeRequest.setTenantId(tenantId);
        changeRequest = changeRequestRepository.save(changeRequest);

        log.info("Shift change request submitted by employee {} for shift {}", employee.getEmployeeCode(), requestedShift.getCode());
        return mapToDto(changeRequest);
    }

    // =====================================================================
    // View Requests
    // =====================================================================

    @Transactional(readOnly = true)
    public List<ShiftChangeRequestDto> getMyRequests(UUID tenantId, UUID userId) {
        Employee employee = employeeRepository.findByUserIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee profile not found"));
        return changeRequestRepository.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(tenantId, employee.getId())
                .stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<ShiftChangeRequestDto> getPendingRequests(UUID tenantId, Pageable pageable) {
        return changeRequestRepository.findByTenantIdAndStatus(tenantId, "PENDING", pageable)
                .map(this::mapToDto);
    }

    // =====================================================================
    // Approve / Reject
    // =====================================================================

    @Transactional
    public ShiftChangeRequestDto approveRequest(UUID tenantId, UUID reviewerUserId, UUID requestId, ShiftChangeReviewRequest review) {
        ShiftChangeRequest changeRequest = changeRequestRepository.findByIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Change request not found"));

        if (!"PENDING".equals(changeRequest.getStatus())) {
            throw new IllegalStateException("Only PENDING requests can be approved");
        }

        // Prevent self-approval
        if (changeRequest.getRequestedBy().getId().equals(reviewerUserId)) {
            throw new AccessDeniedException("Requesters cannot approve their own shift change requests");
        }

        AppUser reviewer = userRepository.findById(reviewerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Reviewer user not found"));

        // Re-validate overlap before creating the new assignment (transactional)
        boolean hasOverlap = assignmentRepository.hasOverlappingActiveAssignment(
                changeRequest.getEmployee().getId(),
                changeRequest.getRequestedEffectiveDate(),
                changeRequest.getRequestedEffectiveEnd());

        if (hasOverlap) {
            changeRequest.setStatus("REJECTED");
            changeRequest.setReviewedBy(reviewer);
            changeRequest.setReviewedAt(Instant.now());
            changeRequest.setReviewerRemarks("Auto-rejected: schedule conflict detected at approval time. " +
                    (review.getRemarks() != null ? review.getRemarks() : ""));
            changeRequestRepository.save(changeRequest);
            throw new IllegalStateException("Shift change request auto-rejected: overlap conflict detected at approval time");
        }

        // Create new shift assignment
        ShiftAssignmentRequest assignReq = new ShiftAssignmentRequest();
        assignReq.setEmployeeId(changeRequest.getEmployee().getId());
        assignReq.setShiftTemplateId(changeRequest.getRequestedShift().getId());
        assignReq.setAssignmentType("TEMPORARY");
        assignReq.setEffectiveStart(changeRequest.getRequestedEffectiveDate());
        assignReq.setEffectiveEnd(changeRequest.getRequestedEffectiveEnd());
        assignReq.setNotes("Created from approved shift change request #" + requestId);

        EmployeeShiftAssignment newAssignment = EmployeeShiftAssignment.builder()
                .employee(changeRequest.getEmployee())
                .shiftTemplate(changeRequest.getRequestedShift())
                .assignmentType("TEMPORARY")
                .effectiveStart(changeRequest.getRequestedEffectiveDate())
                .effectiveEnd(changeRequest.getRequestedEffectiveEnd())
                .assignedBy(reviewer)
                .notes(assignReq.getNotes())
                .build();
        newAssignment.setTenantId(tenantId);
        newAssignment = assignmentRepository.save(newAssignment);

        changeRequest.setStatus("APPROVED");
        changeRequest.setReviewedBy(reviewer);
        changeRequest.setReviewedAt(Instant.now());
        changeRequest.setReviewerRemarks(review.getRemarks());
        changeRequest.setAppliedAssignment(newAssignment);
        changeRequest = changeRequestRepository.save(changeRequest);

        log.info("Shift change request {} approved by {} — new assignment created", requestId, reviewerUserId);
        return mapToDto(changeRequest);
    }

    @Transactional
    public ShiftChangeRequestDto rejectRequest(UUID tenantId, UUID reviewerUserId, UUID requestId, ShiftChangeReviewRequest review) {
        ShiftChangeRequest changeRequest = changeRequestRepository.findByIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Change request not found"));

        if (!"PENDING".equals(changeRequest.getStatus())) {
            throw new IllegalStateException("Only PENDING requests can be rejected");
        }

        if (changeRequest.getRequestedBy().getId().equals(reviewerUserId)) {
            throw new AccessDeniedException("Requesters cannot reject their own shift change requests");
        }

        AppUser reviewer = userRepository.findById(reviewerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Reviewer user not found"));

        changeRequest.setStatus("REJECTED");
        changeRequest.setReviewedBy(reviewer);
        changeRequest.setReviewedAt(Instant.now());
        changeRequest.setReviewerRemarks(review.getRemarks());
        changeRequest = changeRequestRepository.save(changeRequest);

        return mapToDto(changeRequest);
    }

    // =====================================================================
    // Mapping
    // =====================================================================

    public ShiftChangeRequestDto mapToDto(ShiftChangeRequest r) {
        return ShiftChangeRequestDto.builder()
                .id(r.getId())
                .employeeId(r.getEmployee().getId())
                .employeeName(r.getEmployee().getFirstName() + " " + r.getEmployee().getLastName())
                .currentAssignmentId(r.getCurrentAssignment() != null ? r.getCurrentAssignment().getId() : null)
                .currentShiftName(r.getCurrentAssignment() != null ? r.getCurrentAssignment().getShiftTemplate().getName() : null)
                .requestedShiftId(r.getRequestedShift().getId())
                .requestedShiftName(r.getRequestedShift().getName())
                .requestedEffectiveDate(r.getRequestedEffectiveDate())
                .requestedEffectiveEnd(r.getRequestedEffectiveEnd())
                .reason(r.getReason())
                .status(r.getStatus())
                .reviewerRemarks(r.getReviewerRemarks())
                .reviewedAt(r.getReviewedAt())
                .createdAt(r.getCreatedAt())
                .build();
    }
}

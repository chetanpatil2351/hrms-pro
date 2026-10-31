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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftAssignmentService {

    private final EmployeeShiftAssignmentRepository assignmentRepository;
    private final ShiftTemplateRepository shiftTemplateRepository;
    private final ShiftAssignmentHistoryRepository historyRepository;
    private final EmployeeRepository employeeRepository;
    private final AppUserRepository userRepository;

    // =====================================================================
    // Individual Assignment
    // =====================================================================

    @Transactional
    public ShiftAssignmentResponse assignShift(UUID tenantId, UUID actorUserId, ShiftAssignmentRequest request) {
        Employee employee = getActiveEmployee(tenantId, request.getEmployeeId());
        ShiftTemplate shift = getActiveShift(tenantId, request.getShiftTemplateId());
        AppUser actor = userRepository.findById(actorUserId).orElse(null);

        validateDates(request.getEffectiveStart(), request.getEffectiveEnd());

        // Transactional overlap detection (checks ACTIVE assignments only)
        boolean hasOverlap = assignmentRepository.hasOverlappingActiveAssignment(
                employee.getId(), request.getEffectiveStart(), request.getEffectiveEnd());
        if (hasOverlap) {
            throw new IllegalStateException(
                "Employee already has an active shift assignment overlapping the requested period. " +
                "Cancel or supersede the existing assignment first.");
        }

        EmployeeShiftAssignment assignment = EmployeeShiftAssignment.builder()
                .employee(employee)
                .shiftTemplate(shift)
                .assignmentType(request.getAssignmentType() != null ? request.getAssignmentType() : "PERMANENT")
                .effectiveStart(request.getEffectiveStart())
                .effectiveEnd(request.getEffectiveEnd())
                .rotationCycleDays(request.getRotationCycleDays())
                .rotationSequence(request.getRotationSequence())
                .assignedBy(actor)
                .notes(request.getNotes())
                .build();
        assignment.setTenantId(tenantId);
        assignment = assignmentRepository.save(assignment);

        // Record audit history
        recordHistory(tenantId, employee, assignment, null, shift, "CREATED", "Initial assignment", actor,
                request.getEffectiveStart(), request.getEffectiveEnd());

        log.info("Assigned shift '{}' to employee {} effective {}", shift.getCode(), employee.getEmployeeCode(), request.getEffectiveStart());
        return mapToResponse(assignment);
    }

    // =====================================================================
    // Bulk Assignment
    // =====================================================================

    @Transactional
    public Map<String, Object> bulkAssignShift(UUID tenantId, UUID actorUserId, BulkShiftAssignmentRequest request) {
        ShiftTemplate shift = getActiveShift(tenantId, request.getShiftTemplateId());
        AppUser actor = userRepository.findById(actorUserId).orElse(null);
        validateDates(request.getEffectiveStart(), request.getEffectiveEnd());

        List<String> succeeded = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (UUID empId : request.getEmployeeIds()) {
            try {
                Employee employee = getActiveEmployee(tenantId, empId);
                boolean hasOverlap = assignmentRepository.hasOverlappingActiveAssignment(
                        employee.getId(), request.getEffectiveStart(), request.getEffectiveEnd());

                if (hasOverlap) {
                    if (request.isSkipConflicting()) {
                        skipped.add(employee.getEmployeeCode() + " (conflict)");
                        continue;
                    } else {
                        failed.add(employee.getEmployeeCode() + " (overlap conflict)");
                        continue;
                    }
                }

                EmployeeShiftAssignment assignment = EmployeeShiftAssignment.builder()
                        .employee(employee)
                        .shiftTemplate(shift)
                        .assignmentType(request.getAssignmentType() != null ? request.getAssignmentType() : "PERMANENT")
                        .effectiveStart(request.getEffectiveStart())
                        .effectiveEnd(request.getEffectiveEnd())
                        .assignedBy(actor)
                        .notes(request.getNotes())
                        .build();
                assignment.setTenantId(tenantId);
                assignmentRepository.save(assignment);
                recordHistory(tenantId, employee, assignment, null, shift, "BULK_CREATED", "Bulk assignment", actor,
                        request.getEffectiveStart(), request.getEffectiveEnd());
                succeeded.add(employee.getEmployeeCode());

            } catch (Exception ex) {
                log.warn("Bulk shift assignment failed for employee {}: {}", empId, ex.getMessage());
                failed.add(empId + " (" + ex.getMessage() + ")");
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("succeeded", succeeded.size());
        result.put("skipped", skipped.size());
        result.put("failed", failed.size());
        result.put("succeededEmployees", succeeded);
        result.put("skippedEmployees", skipped);
        result.put("failedEmployees", failed);
        return result;
    }

    // =====================================================================
    // Read & Update
    // =====================================================================

    @Transactional(readOnly = true)
    public Page<ShiftAssignmentResponse> getAssignments(UUID tenantId, Pageable pageable) {
        return assignmentRepository.findByTenantId(tenantId, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<ShiftAssignmentResponse> getAssignmentsForEmployee(UUID tenantId, UUID employeeId) {
        return assignmentRepository.findByTenantIdAndEmployeeIdOrderByEffectiveStartDesc(tenantId, employeeId)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional
    public ShiftAssignmentResponse updateAssignment(UUID tenantId, UUID assignmentId, UUID actorUserId, ShiftAssignmentRequest request) {
        EmployeeShiftAssignment assignment = assignmentRepository.findByIdAndTenantId(assignmentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift assignment not found"));

        if (!"ACTIVE".equals(assignment.getStatus())) {
            throw new IllegalStateException("Only ACTIVE assignments can be updated");
        }

        validateDates(request.getEffectiveStart(), request.getEffectiveEnd());

        // Overlap check excluding current assignment
        boolean hasOverlap = assignmentRepository.hasOverlappingActiveAssignmentExcluding(
                assignment.getEmployee().getId(), request.getEffectiveStart(), request.getEffectiveEnd(), assignmentId);
        if (hasOverlap) {
            throw new IllegalStateException("Updated date range overlaps with another active shift assignment");
        }

        ShiftTemplate oldShift = assignment.getShiftTemplate();
        ShiftTemplate newShift = getActiveShift(tenantId, request.getShiftTemplateId());
        AppUser actor = userRepository.findById(actorUserId).orElse(null);

        assignment.setShiftTemplate(newShift);
        assignment.setEffectiveStart(request.getEffectiveStart());
        assignment.setEffectiveEnd(request.getEffectiveEnd());
        assignment.setAssignmentType(request.getAssignmentType() != null ? request.getAssignmentType() : assignment.getAssignmentType());
        assignment.setNotes(request.getNotes());
        assignment = assignmentRepository.save(assignment);

        recordHistory(tenantId, assignment.getEmployee(), assignment, oldShift, newShift, "UPDATED",
                "Assignment updated", actor, request.getEffectiveStart(), request.getEffectiveEnd());

        return mapToResponse(assignment);
    }

    @Transactional
    public ShiftAssignmentResponse cancelAssignment(UUID tenantId, UUID assignmentId, UUID actorUserId, String reason) {
        EmployeeShiftAssignment assignment = assignmentRepository.findByIdAndTenantId(assignmentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift assignment not found"));

        if ("CANCELLED".equals(assignment.getStatus())) {
            throw new IllegalStateException("Assignment is already cancelled");
        }

        AppUser actor = userRepository.findById(actorUserId).orElse(null);
        assignment.setStatus("CANCELLED");
        assignment.setCancelledAt(Instant.now());
        assignment.setCancelledBy(actor);
        assignment.setCancellationReason(reason);
        assignment = assignmentRepository.save(assignment);

        recordHistory(tenantId, assignment.getEmployee(), assignment, assignment.getShiftTemplate(), null,
                "CANCELLED", reason, actor, assignment.getEffectiveStart(), assignment.getEffectiveEnd());

        return mapToResponse(assignment);
    }

    // =====================================================================
    // Resolve effective shift for a date (used by attendance integration)
    // =====================================================================

    @Transactional(readOnly = true)
    public Optional<EmployeeShiftAssignment> resolveEffectiveAssignment(UUID employeeId, LocalDate date) {
        List<EmployeeShiftAssignment> candidates = assignmentRepository.findEffectiveAssignmentsForDate(employeeId, date);
        return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(0));
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private Employee getActiveEmployee(UUID tenantId, UUID employeeId) {
        return employeeRepository.findByIdAndTenantId(employeeId, tenantId)
                .filter(e -> "ACTIVE".equals(e.getEmploymentStatus()) || "PROBATION".equals(e.getEmploymentStatus()))
                .orElseThrow(() -> new IllegalArgumentException("Employee not found or not active in this company"));
    }

    private ShiftTemplate getActiveShift(UUID tenantId, UUID shiftId) {
        return shiftTemplateRepository.findByIdAndTenantId(shiftId, tenantId)
                .filter(ShiftTemplate::getIsActive)
                .orElseThrow(() -> new IllegalArgumentException("Shift template not found or inactive"));
    }

    private void validateDates(LocalDate start, LocalDate end) {
        if (start == null) throw new IllegalArgumentException("Effective start date is required");
        if (end != null && end.isBefore(start))
            throw new IllegalArgumentException("Effective end date must be after start date");
    }

    private void recordHistory(UUID tenantId, Employee employee, EmployeeShiftAssignment assignment,
                               ShiftTemplate oldShift, ShiftTemplate newShift,
                               String action, String reason, AppUser actor,
                               LocalDate effectiveStart, LocalDate effectiveEnd) {
        ShiftAssignmentHistory history = ShiftAssignmentHistory.builder()
                .tenantId(tenantId)
                .employee(employee)
                .assignment(assignment)
                .oldShift(oldShift)
                .newShift(newShift)
                .action(action)
                .changeReason(reason)
                .performedBy(actor)
                .effectiveStart(effectiveStart)
                .effectiveEnd(effectiveEnd)
                .build();
        historyRepository.save(history);
    }

    public ShiftAssignmentResponse mapToResponse(EmployeeShiftAssignment a) {
        return ShiftAssignmentResponse.builder()
                .id(a.getId())
                .tenantId(a.getTenantId())
                .employeeId(a.getEmployee().getId())
                .employeeName(a.getEmployee().getFirstName() + " " + a.getEmployee().getLastName())
                .employeeCode(a.getEmployee().getEmployeeCode())
                .shiftTemplateId(a.getShiftTemplate().getId())
                .shiftName(a.getShiftTemplate().getName())
                .shiftCode(a.getShiftTemplate().getCode())
                .assignmentType(a.getAssignmentType())
                .effectiveStart(a.getEffectiveStart())
                .effectiveEnd(a.getEffectiveEnd())
                .status(a.getStatus())
                .rotationCycleDays(a.getRotationCycleDays())
                .rotationSequence(a.getRotationSequence())
                .notes(a.getNotes())
                .build();
    }
}

package com.hrmspro.modules.shift;

import com.hrmspro.common.exception.ResourceNotFoundException;
import com.hrmspro.modules.employee.Employee;
import com.hrmspro.modules.employee.EmployeeRepository;
import com.hrmspro.modules.shift.dto.RosterGenerationRequest;
import com.hrmspro.modules.shift.dto.RosterResponse;
import com.hrmspro.modules.user.AppUser;
import com.hrmspro.modules.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftRosterService {

    private static final int MAX_GENERATION_DAYS = 31;

    private final ShiftRosterRepository rosterRepository;
    private final EmployeeShiftAssignmentRepository assignmentRepository;
    private final ShiftTemplateRepository shiftTemplateRepository;
    private final EmployeeRepository employeeRepository;
    private final AppUserRepository userRepository;
    private final ShiftService shiftService;

    // =====================================================================
    // Roster Generation (DRAFT status)
    // =====================================================================

    @Transactional
    public Map<String, Object> generateRoster(UUID tenantId, UUID actorUserId, RosterGenerationRequest request) {
        validateGenerationRequest(request);
        AppUser actor = userRepository.findById(actorUserId).orElse(null);

        // Collect employees in scope
        List<Employee> employees = resolveEmployees(tenantId, request);
        if (employees.isEmpty()) {
            return Map.of("message", "No active employees found in scope", "generated", 0);
        }

        int generated = 0;
        int skipped = 0;
        LocalDate cursor = request.getFromDate();

        while (!cursor.isAfter(request.getToDate())) {
            for (Employee employee : employees) {
                LocalDate rosterDate = cursor;
                Optional<ShiftRoster> existing = rosterRepository.findByTenantIdAndEmployeeIdAndRosterDate(
                        tenantId, employee.getId(), rosterDate);

                if (existing.isPresent()) {
                    ShiftRoster ex = existing.get();
                    if ("PUBLISHED".equals(ex.getStatus())) {
                        skipped++; // Never overwrite published rosters
                        continue;
                    }
                    if ("DRAFT".equals(ex.getStatus()) && !request.isOverwriteDrafts()) {
                        skipped++;
                        continue;
                    }
                    if ("DRAFT".equals(ex.getStatus()) && request.isOverwriteDrafts()) {
                        rosterRepository.delete(ex);
                    }
                }

                // Resolve effective shift assignment for this date
                List<EmployeeShiftAssignment> assignments = assignmentRepository.findEffectiveAssignmentsForDate(
                        employee.getId(), rosterDate);

                if (assignments.isEmpty()) {
                    skipped++;
                    continue;
                }

                EmployeeShiftAssignment assignment = assignments.get(0);
                ShiftTemplate template = assignment.getShiftTemplate();

                boolean isWeeklyOff = shiftService.isWeeklyOff(template, rosterDate);

                Instant scheduledStart = null;
                Instant scheduledEnd = null;
                String snapshot = null;

                if (!isWeeklyOff) {
                    ZoneId zoneId = ZoneId.of(template.getTimezone());
                    scheduledStart = ShiftService.computeScheduledStart(rosterDate, template.getStartTime(), zoneId);
                    scheduledEnd = ShiftService.computeScheduledEnd(rosterDate, template.getEndTime(), template.getIsOvernight(), zoneId);
                    snapshot = shiftService.buildShiftSnapshot(template);
                }

                ShiftRoster roster = ShiftRoster.builder()
                        .employee(employee)
                        .shiftAssignment(assignment)
                        .shiftTemplate(template)
                        .rosterDate(rosterDate)
                        .isWeeklyOff(isWeeklyOff)
                        .scheduledStartUtc(scheduledStart)
                        .scheduledEndUtc(scheduledEnd)
                        .scheduledMinutes(isWeeklyOff ? 0 : template.getTotalScheduledMinutes())
                        .shiftSnapshot(snapshot)
                        .status("DRAFT")
                        .generatedBy(actor)
                        .build();
                roster.setTenantId(tenantId);
                rosterRepository.save(roster);
                generated++;
            }
            cursor = cursor.plusDays(1);
        }

        log.info("Generated {} roster records (skipped: {}) for tenant {} period {} to {}",
                generated, skipped, tenantId, request.getFromDate(), request.getToDate());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generated", generated);
        result.put("skipped", skipped);
        result.put("from", request.getFromDate().toString());
        result.put("to", request.getToDate().toString());
        result.put("status", "DRAFT");
        return result;
    }

    // =====================================================================
    // Publish / Cancel
    // =====================================================================

    @Transactional
    public Map<String, Object> publishRosters(UUID tenantId, UUID actorUserId, LocalDate fromDate, LocalDate toDate) {
        AppUser actor = userRepository.findById(actorUserId).orElse(null);
        List<ShiftRoster> drafts = rosterRepository.findByTenantIdAndRosterDateBetweenAndStatusOrderByRosterDateAsc(
                tenantId, fromDate, toDate, "DRAFT");

        int published = 0;
        for (ShiftRoster r : drafts) {
            r.setStatus("PUBLISHED");
            r.setPublishedBy(actor);
            r.setPublishedAt(Instant.now());
            rosterRepository.save(r);
            published++;
        }
        log.info("Published {} roster records for tenant {}", published, tenantId);
        return Map.of("published", published, "from", fromDate.toString(), "to", toDate.toString());
    }

    @Transactional
    public Map<String, Object> cancelRosters(UUID tenantId, UUID actorUserId, LocalDate fromDate, LocalDate toDate) {
        AppUser actor = userRepository.findById(actorUserId).orElse(null);
        List<ShiftRoster> toCancel = rosterRepository.findByTenantIdAndRosterDateBetweenAndStatusOrderByRosterDateAsc(
                tenantId, fromDate, toDate, "DRAFT");

        int cancelled = 0;
        for (ShiftRoster r : toCancel) {
            r.setStatus("CANCELLED");
            r.setCancelledBy(actor);
            r.setCancelledAt(Instant.now());
            rosterRepository.save(r);
            cancelled++;
        }
        return Map.of("cancelled", cancelled);
    }

    // =====================================================================
    // Queries
    // =====================================================================

    @Transactional(readOnly = true)
    public List<RosterResponse> getRosters(UUID tenantId, LocalDate from, LocalDate to) {
        return rosterRepository.findByTenantIdAndRosterDateBetweenAndStatusOrderByRosterDateAsc(tenantId, from, to, "PUBLISHED")
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RosterResponse> getRostersForEmployee(UUID tenantId, UUID employeeId, LocalDate from, LocalDate to) {
        return rosterRepository.findByTenantIdAndEmployeeIdAndRosterDateBetweenOrderByRosterDateAsc(tenantId, employeeId, from, to)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RosterResponse> getRostersForBranch(UUID tenantId, UUID branchId, LocalDate from, LocalDate to) {
        return rosterRepository.findByTenantIdAndBranchAndDateRange(tenantId, branchId, from, to)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private void validateGenerationRequest(RosterGenerationRequest req) {
        if (req.getFromDate() == null || req.getToDate() == null)
            throw new IllegalArgumentException("fromDate and toDate are required");
        if (req.getToDate().isBefore(req.getFromDate()))
            throw new IllegalArgumentException("toDate must be after fromDate");
        long days = req.getFromDate().until(req.getToDate()).getDays() + 1;
        if (days > MAX_GENERATION_DAYS)
            throw new IllegalArgumentException("Roster generation span cannot exceed " + MAX_GENERATION_DAYS + " days");
    }

    @SuppressWarnings("unchecked")
    private List<Employee> resolveEmployees(UUID tenantId, RosterGenerationRequest request) {
        if (request.getEmployeeIds() != null && !request.getEmployeeIds().isEmpty()) {
            return request.getEmployeeIds().stream()
                    .map(id -> employeeRepository.findByIdAndTenantId(id, tenantId).orElse(null))
                    .filter(Objects::nonNull)
                    .filter(e -> "ACTIVE".equals(e.getEmploymentStatus()) || "PROBATION".equals(e.getEmploymentStatus()))
                    .collect(Collectors.toList());
        }
        // All active employees in tenant (scoped by branch if provided)
        if (request.getBranchId() != null) {
            return employeeRepository.findByTenantIdAndBranchId(tenantId, request.getBranchId())
                    .stream()
                    .filter(e -> "ACTIVE".equals(e.getEmploymentStatus()) || "PROBATION".equals(e.getEmploymentStatus()))
                    .collect(Collectors.toList());
        }
        return employeeRepository.findByTenantId(tenantId, PageRequest.of(0, 1000)).getContent()
                .stream()
                .filter(e -> "ACTIVE".equals(e.getEmploymentStatus()) || "PROBATION".equals(e.getEmploymentStatus()))
                .collect(Collectors.toList());
    }

    public RosterResponse mapToResponse(ShiftRoster r) {
        return RosterResponse.builder()
                .id(r.getId())
                .employeeId(r.getEmployee().getId())
                .employeeName(r.getEmployee().getFirstName() + " " + r.getEmployee().getLastName())
                .employeeCode(r.getEmployee().getEmployeeCode())
                .shiftTemplateId(r.getShiftTemplate() != null ? r.getShiftTemplate().getId() : null)
                .shiftName(r.getShiftTemplate() != null ? r.getShiftTemplate().getName() : null)
                .shiftCode(r.getShiftTemplate() != null ? r.getShiftTemplate().getCode() : null)
                .rosterDate(r.getRosterDate())
                .isWeeklyOff(r.getIsWeeklyOff())
                .isHoliday(r.getIsHoliday())
                .scheduledStartUtc(r.getScheduledStartUtc())
                .scheduledEndUtc(r.getScheduledEndUtc())
                .scheduledMinutes(r.getScheduledMinutes())
                .status(r.getStatus())
                .publishedAt(r.getPublishedAt())
                .notes(r.getNotes())
                .build();
    }
}

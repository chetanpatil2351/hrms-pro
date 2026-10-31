package com.hrmspro.modules.shift;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrmspro.common.exception.ResourceNotFoundException;
import com.hrmspro.modules.shift.dto.ShiftTemplateRequest;
import com.hrmspro.modules.shift.dto.ShiftTemplateResponse;
import com.hrmspro.modules.tenant.Branch;
import com.hrmspro.modules.tenant.BranchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftService {

    private static final Set<String> VALID_DAY_NAMES = Arrays.stream(DayOfWeek.values())
            .map(DayOfWeek::name)
            .collect(Collectors.toSet());

    private final ShiftTemplateRepository shiftTemplateRepository;
    private final BranchRepository branchRepository;
    private final ObjectMapper objectMapper;

    // =====================================================================
    // Shift Template CRUD
    // =====================================================================

    @Transactional
    public ShiftTemplateResponse createShift(UUID tenantId, UUID actorUserId, ShiftTemplateRequest request) {
        validateRequest(request);

        String code = request.getCode().toUpperCase().trim();
        if (shiftTemplateRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new IllegalArgumentException("Shift code '" + code + "' already exists in this company");
        }

        Branch branch = null;
        if (request.getBranchId() != null) {
            branch = branchRepository.findById(request.getBranchId())
                    .filter(b -> b.getTenantId().equals(tenantId))
                    .orElseThrow(() -> new IllegalArgumentException("Branch does not belong to this company"));
        }

        String weeklyOffJson = serializeWeeklyOffDays(request.getWeeklyOffDays());
        int scheduledMinutes = computeScheduledMinutes(request.getStartTime(), request.getEndTime(),
                Boolean.TRUE.equals(request.getIsOvernight()), request.getTimezone());

        ShiftTemplate template = ShiftTemplate.builder()
                .branch(branch)
                .name(request.getName().trim())
                .code(code)
                .description(request.getDescription())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .isOvernight(Boolean.TRUE.equals(request.getIsOvernight()))
                .timezone(request.getTimezone() != null ? request.getTimezone() : "Asia/Kolkata")
                .totalScheduledMinutes(scheduledMinutes)
                .gracePeriodMinutes(request.getGracePeriodMinutes() != null ? request.getGracePeriodMinutes() : 0)
                .lateArrivalPolicy(request.getLateArrivalPolicy() != null ? request.getLateArrivalPolicy() : "MARK_LATE")
                .earlyDeparturePolicy(request.getEarlyDeparturePolicy() != null ? request.getEarlyDeparturePolicy() : "MARK_EARLY")
                .breakPolicy(request.getBreakPolicy() != null ? request.getBreakPolicy() : "FLEXIBLE")
                .breakAllowedMinutes(request.getBreakAllowedMinutes() != null ? request.getBreakAllowedMinutes() : 0)
                .weeklyOffDays(weeklyOffJson)
                .effectiveFrom(request.getEffectiveFrom() != null ? request.getEffectiveFrom() : LocalDate.now())
                .effectiveTo(request.getEffectiveTo())
                .isActive(request.getIsActive() == null || request.getIsActive())
                .build();
        template.setTenantId(tenantId);
        template = shiftTemplateRepository.save(template);

        log.info("Created shift template '{}' (code={}) for tenant {}", template.getName(), template.getCode(), tenantId);
        return mapToResponse(template);
    }

    @Transactional(readOnly = true)
    public Page<ShiftTemplateResponse> getShifts(UUID tenantId, Pageable pageable) {
        return shiftTemplateRepository.findByTenantId(tenantId, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public ShiftTemplateResponse getShiftById(UUID tenantId, UUID shiftId) {
        ShiftTemplate template = shiftTemplateRepository.findByIdAndTenantId(shiftId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift template not found"));
        return mapToResponse(template);
    }

    @Transactional
    public ShiftTemplateResponse updateShift(UUID tenantId, UUID shiftId, ShiftTemplateRequest request) {
        ShiftTemplate template = shiftTemplateRepository.findByIdAndTenantId(shiftId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift template not found"));

        validateRequest(request);

        String code = request.getCode().toUpperCase().trim();
        if (shiftTemplateRepository.existsByTenantIdAndCodeAndIdNot(tenantId, code, shiftId)) {
            throw new IllegalArgumentException("Shift code '" + code + "' is already used by another shift");
        }

        Branch branch = null;
        if (request.getBranchId() != null) {
            branch = branchRepository.findById(request.getBranchId())
                    .filter(b -> b.getTenantId().equals(tenantId))
                    .orElseThrow(() -> new IllegalArgumentException("Branch does not belong to this company"));
        }

        int scheduledMinutes = computeScheduledMinutes(request.getStartTime(), request.getEndTime(),
                Boolean.TRUE.equals(request.getIsOvernight()), request.getTimezone());

        template.setBranch(branch);
        template.setName(request.getName().trim());
        template.setCode(code);
        template.setDescription(request.getDescription());
        template.setStartTime(request.getStartTime());
        template.setEndTime(request.getEndTime());
        template.setIsOvernight(Boolean.TRUE.equals(request.getIsOvernight()));
        template.setTimezone(request.getTimezone() != null ? request.getTimezone() : "Asia/Kolkata");
        template.setTotalScheduledMinutes(scheduledMinutes);
        template.setGracePeriodMinutes(request.getGracePeriodMinutes() != null ? request.getGracePeriodMinutes() : 0);
        template.setLateArrivalPolicy(request.getLateArrivalPolicy() != null ? request.getLateArrivalPolicy() : "MARK_LATE");
        template.setEarlyDeparturePolicy(request.getEarlyDeparturePolicy() != null ? request.getEarlyDeparturePolicy() : "MARK_EARLY");
        template.setBreakPolicy(request.getBreakPolicy() != null ? request.getBreakPolicy() : "FLEXIBLE");
        template.setBreakAllowedMinutes(request.getBreakAllowedMinutes() != null ? request.getBreakAllowedMinutes() : 0);
        template.setWeeklyOffDays(serializeWeeklyOffDays(request.getWeeklyOffDays()));
        template.setEffectiveFrom(request.getEffectiveFrom() != null ? request.getEffectiveFrom() : template.getEffectiveFrom());
        template.setEffectiveTo(request.getEffectiveTo());
        if (request.getIsActive() != null) template.setIsActive(request.getIsActive());

        template = shiftTemplateRepository.save(template);
        log.info("Updated shift template '{}' for tenant {}", template.getCode(), tenantId);
        return mapToResponse(template);
    }

    @Transactional
    public ShiftTemplateResponse patchStatus(UUID tenantId, UUID shiftId, boolean active) {
        ShiftTemplate template = shiftTemplateRepository.findByIdAndTenantId(shiftId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift template not found"));
        template.setIsActive(active);
        template = shiftTemplateRepository.save(template);
        return mapToResponse(template);
    }

    // =====================================================================
    // DST-aware Scheduled Minutes Calculation
    // =====================================================================

    /**
     * Computes scheduled working minutes using timezone-aware ZonedDateTime arithmetic.
     *
     * DST Policy (documented):
     * - Spring-forward (23h day): The shift scheduled duration is computed from
     *   actual wall-clock instant difference. If the shift window spans the gap hour,
     *   the scheduled minutes reflect the shorter actual duration.
     * - Fall-back (25h day): The shift window spans the extra hour. The computed
     *   duration reflects the longer actual duration. Attendance credits the actual
     *   worked time; excess above scheduled minutes is flagged as overtime candidate
     *   but NOT auto-credited as paid overtime (payroll phase handles that).
     *
     * @param startTime  local start time of the shift
     * @param endTime    local end time of the shift
     * @param isOvernight true if end time is next day
     * @param tzId       IANA timezone string
     * @return scheduled minutes for a standard (non-DST-transition) day
     */
    public static int computeScheduledMinutes(LocalTime startTime, LocalTime endTime, boolean isOvernight, String tzId) {
        ZoneId zoneId = ZoneId.of(tzId != null ? tzId : "Asia/Kolkata");
        LocalDate referenceDate = LocalDate.now(zoneId);
        ZonedDateTime start = ZonedDateTime.of(referenceDate, startTime, zoneId);
        ZonedDateTime end = ZonedDateTime.of(isOvernight ? referenceDate.plusDays(1) : referenceDate, endTime, zoneId);
        return (int) Duration.between(start, end).toMinutes();
    }

    /**
     * Compute timezone-aware scheduled start/end instants for a specific roster date.
     * Handles overnight shifts and DST transitions correctly.
     */
    public static java.time.Instant computeScheduledStart(LocalDate date, LocalTime startTime, ZoneId zoneId) {
        return ZonedDateTime.of(date, startTime, zoneId).toInstant();
    }

    public static java.time.Instant computeScheduledEnd(LocalDate date, LocalTime endTime, boolean isOvernight, ZoneId zoneId) {
        LocalDate endDate = isOvernight ? date.plusDays(1) : date;
        return ZonedDateTime.of(endDate, endTime, zoneId).toInstant();
    }

    // =====================================================================
    // Weekly Off Day Helpers
    // =====================================================================

    public String serializeWeeklyOffDays(List<String> days) {
        if (days == null || days.isEmpty()) return "[]";
        // Validate and deduplicate
        Set<String> seen = new LinkedHashSet<>();
        for (String day : days) {
            String upper = day.toUpperCase().trim();
            if (!VALID_DAY_NAMES.contains(upper)) {
                throw new IllegalArgumentException("Invalid day name: '" + day + "'. Allowed: " + VALID_DAY_NAMES);
            }
            seen.add(upper);
        }
        try {
            return objectMapper.writeValueAsString(new ArrayList<>(seen));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize weekly off days", e);
        }
    }

    public List<String> parseWeeklyOffDays(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    public boolean isWeeklyOff(ShiftTemplate template, LocalDate date) {
        List<String> offDays = parseWeeklyOffDays(template.getWeeklyOffDays());
        String dayName = date.getDayOfWeek().name();
        return offDays.contains(dayName);
    }

    // =====================================================================
    // Snapshot for Roster/Attendance
    // =====================================================================

    public String buildShiftSnapshot(ShiftTemplate template) {
        try {
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("shiftId", template.getId() != null ? template.getId().toString() : null);
            snap.put("code", template.getCode());
            snap.put("name", template.getName());
            snap.put("timezone", template.getTimezone());
            snap.put("startTime", template.getStartTime().toString());
            snap.put("endTime", template.getEndTime().toString());
            snap.put("isOvernight", template.getIsOvernight());
            snap.put("totalScheduledMinutes", template.getTotalScheduledMinutes());
            snap.put("gracePeriodMinutes", template.getGracePeriodMinutes());
            snap.put("lateArrivalPolicy", template.getLateArrivalPolicy());
            snap.put("earlyDeparturePolicy", template.getEarlyDeparturePolicy());
            snap.put("weeklyOffDays", parseWeeklyOffDays(template.getWeeklyOffDays()));
            return objectMapper.writeValueAsString(snap);
        } catch (Exception e) {
            return "{}";
        }
    }

    // =====================================================================
    // Mapping
    // =====================================================================

    private void validateRequest(ShiftTemplateRequest request) {
        if (request.getName() == null || request.getName().isBlank())
            throw new IllegalArgumentException("Shift name is required");
        if (request.getCode() == null || request.getCode().isBlank())
            throw new IllegalArgumentException("Shift code is required");
        if (request.getStartTime() == null || request.getEndTime() == null)
            throw new IllegalArgumentException("Shift start and end times are required");
        if (request.getTimezone() != null) {
            try { ZoneId.of(request.getTimezone()); }
            catch (Exception e) { throw new IllegalArgumentException("Invalid IANA timezone: " + request.getTimezone()); }
        }
    }

    public ShiftTemplateResponse mapToResponse(ShiftTemplate t) {
        return ShiftTemplateResponse.builder()
                .id(t.getId())
                .tenantId(t.getTenantId())
                .branchId(t.getBranch() != null ? t.getBranch().getId() : null)
                .branchName(t.getBranch() != null ? t.getBranch().getName() : null)
                .name(t.getName())
                .code(t.getCode())
                .description(t.getDescription())
                .startTime(t.getStartTime())
                .endTime(t.getEndTime())
                .isOvernight(t.getIsOvernight())
                .timezone(t.getTimezone())
                .totalScheduledMinutes(t.getTotalScheduledMinutes())
                .gracePeriodMinutes(t.getGracePeriodMinutes())
                .lateArrivalPolicy(t.getLateArrivalPolicy())
                .earlyDeparturePolicy(t.getEarlyDeparturePolicy())
                .breakPolicy(t.getBreakPolicy())
                .breakAllowedMinutes(t.getBreakAllowedMinutes())
                .weeklyOffDays(parseWeeklyOffDays(t.getWeeklyOffDays()))
                .effectiveFrom(t.getEffectiveFrom())
                .effectiveTo(t.getEffectiveTo())
                .isActive(t.getIsActive())
                .build();
    }
}

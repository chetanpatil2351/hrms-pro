package com.hrmspro.modules.leave;

import com.hrmspro.common.exception.ResourceNotFoundException;
import com.hrmspro.modules.leave.dto.HolidayDto;
import com.hrmspro.modules.tenant.Branch;
import com.hrmspro.modules.tenant.BranchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HolidayService {

    private final HolidayRepository holidayRepository;
    private final BranchRepository branchRepository;

    @Transactional
    public HolidayDto createHoliday(UUID tenantId, HolidayDto request) {
        Branch branch = null;
        if (request.getBranchId() != null) {
            branch = branchRepository.findById(request.getBranchId())
                    .filter(b -> b.getTenantId().equals(tenantId))
                    .orElseThrow(() -> new IllegalArgumentException("Branch does not belong to this company"));
        }

        Holiday holiday = Holiday.builder()
                .branch(branch)
                .name(request.getName().trim())
                .holidayDate(request.getHolidayDate())
                .description(request.getDescription())
                .holidayType(request.getHolidayType() != null ? request.getHolidayType() : "PUBLIC")
                .isRecurring(Boolean.TRUE.equals(request.getIsRecurring()))
                .isActive(request.getIsActive() == null || request.getIsActive())
                .build();
        holiday.setTenantId(tenantId);
        holiday = holidayRepository.save(holiday);

        return mapToDto(holiday);
    }

    @Transactional(readOnly = true)
    public Page<HolidayDto> getHolidays(UUID tenantId, Pageable pageable) {
        return holidayRepository.findByTenantId(tenantId, pageable).map(this::mapToDto);
    }

    @Transactional(readOnly = true)
    public HolidayDto getHolidayById(UUID tenantId, UUID id) {
        Holiday holiday = holidayRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday not found"));
        return mapToDto(holiday);
    }

    @Transactional
    public HolidayDto updateHoliday(UUID tenantId, UUID id, HolidayDto request) {
        Holiday holiday = holidayRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday not found"));

        Branch branch = null;
        if (request.getBranchId() != null) {
            branch = branchRepository.findById(request.getBranchId())
                    .filter(b -> b.getTenantId().equals(tenantId))
                    .orElseThrow(() -> new IllegalArgumentException("Branch does not belong to this company"));
        }

        holiday.setBranch(branch);
        holiday.setName(request.getName().trim());
        holiday.setHolidayDate(request.getHolidayDate());
        holiday.setDescription(request.getDescription());
        holiday.setHolidayType(request.getHolidayType() != null ? request.getHolidayType() : holiday.getHolidayType());
        if (request.getIsRecurring() != null) holiday.setIsRecurring(request.getIsRecurring());
        if (request.getIsActive() != null) holiday.setIsActive(request.getIsActive());

        holiday = holidayRepository.save(holiday);
        return mapToDto(holiday);
    }

    @Transactional
    public void deleteHoliday(UUID tenantId, UUID id) {
        Holiday holiday = holidayRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday not found"));
        // Soft delete / deactivate to preserve historical references
        holiday.setIsActive(false);
        holidayRepository.save(holiday);
    }

    @Transactional(readOnly = true)
    public List<Holiday> getHolidaysForEmployeeDateRange(UUID tenantId, UUID branchId, LocalDate start, LocalDate end) {
        return holidayRepository.findHolidaysForBranchAndDateRange(tenantId, branchId, start, end);
    }

    public HolidayDto mapToDto(Holiday h) {
        return HolidayDto.builder()
                .id(h.getId())
                .branchId(h.getBranch() != null ? h.getBranch().getId() : null)
                .branchName(h.getBranch() != null ? h.getBranch().getName() : null)
                .name(h.getName())
                .holidayDate(h.getHolidayDate())
                .description(h.getDescription())
                .holidayType(h.getHolidayType())
                .isRecurring(h.getIsRecurring())
                .isActive(h.getIsActive())
                .build();
    }
}

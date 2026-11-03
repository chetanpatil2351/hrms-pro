package com.hrmspro.modules.payroll;

import com.hrmspro.common.context.TenantContext;
import com.hrmspro.common.exception.ResourceNotFoundException;
import com.hrmspro.modules.employee.Employee;
import com.hrmspro.modules.employee.EmployeeRepository;
import com.hrmspro.modules.payroll.dto.*;
import com.hrmspro.modules.user.AppUser;
import com.hrmspro.modules.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalaryStructureService {

    private final SalaryComponentRepository componentRepository;
    private final SalaryStructureRepository structureRepository;
    private final SalaryStructureComponentRepository structureComponentRepository;
    private final EmployeeSalaryStructureRepository employeeSalaryStructureRepository;
    private final SalaryRevisionHistoryRepository revisionHistoryRepository;
    private final EmployeeRepository employeeRepository;
    private final AppUserRepository appUserRepository;

    private UUID getRequiredTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context not set");
        }
        return tenantId;
    }

    // --- Component Management ---

    @Transactional
    public SalaryComponentDto createComponent(SalaryComponentDto dto) {
        UUID tenantId = getRequiredTenantId();
        if (componentRepository.existsByTenantIdAndCode(tenantId, dto.getCode())) {
            throw new IllegalArgumentException("Salary component with code " + dto.getCode() + " already exists");
        }

        SalaryComponent component = SalaryComponent.builder()
                .name(dto.getName())
                .code(dto.getCode().toUpperCase())
                .type(dto.getType().toUpperCase())
                .calculationType(dto.getCalculationType() != null ? dto.getCalculationType().toUpperCase() : "FIXED_AMOUNT")
                .calculationBase(dto.getCalculationBase() != null ? dto.getCalculationBase().toUpperCase() : null)
                .percentageRate(dto.getPercentageRate())
                .isTaxable(dto.getIsTaxable() != null ? dto.getIsTaxable() : true)
                .includeInGross(dto.getIncludeInGross() != null ? dto.getIncludeInGross() : true)
                .includeInCTC(dto.getIncludeInCTC() != null ? dto.getIncludeInCTC() : true)
                .isStatutory(dto.getIsStatutory() != null ? dto.getIsStatutory() : false)
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();
        component.setTenantId(tenantId);
        component = componentRepository.save(component);

        return toComponentDto(component);
    }

    public List<SalaryComponentDto> getActiveComponents() {
        UUID tenantId = getRequiredTenantId();
        return componentRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .stream()
                .map(this::toComponentDto)
                .collect(Collectors.toList());
    }

    // --- Salary Structure Management ---

    @Transactional
    public SalaryStructureDto createStructure(SalaryStructureDto dto) {
        UUID tenantId = getRequiredTenantId();
        if (structureRepository.existsByTenantIdAndCode(tenantId, dto.getCode())) {
            throw new IllegalArgumentException("Salary structure with code " + dto.getCode() + " already exists");
        }

        SalaryStructure structure = SalaryStructure.builder()
                .name(dto.getName())
                .code(dto.getCode().toUpperCase())
                .description(dto.getDescription())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();
        structure.setTenantId(tenantId);
        structure = structureRepository.save(structure);

        if (dto.getComponents() != null && !dto.getComponents().isEmpty()) {
            for (SalaryStructureDto.ComponentItemDto item : dto.getComponents()) {
                SalaryComponent comp = componentRepository.findByIdAndTenantId(item.getComponentId(), tenantId)
                        .orElseThrow(() -> new ResourceNotFoundException("Salary component not found: " + item.getComponentId()));

                SalaryStructureComponent sc = SalaryStructureComponent.builder()
                        .salaryStructure(structure)
                        .salaryComponent(comp)
                        .defaultAmount(item.getDefaultAmount() != null ? item.getDefaultAmount() : BigDecimal.ZERO)
                        .percentageRate(item.getPercentageRate() != null ? item.getPercentageRate() : comp.getPercentageRate())
                        .displayOrder(item.getDisplayOrder() != null ? item.getDisplayOrder() : 1)
                        .build();
                sc.setTenantId(tenantId);
                structureComponentRepository.save(sc);
            }
        }

        return getStructureById(structure.getId());
    }

    public SalaryStructureDto getStructureById(UUID structureId) {
        UUID tenantId = getRequiredTenantId();
        SalaryStructure structure = structureRepository.findByIdAndTenantId(structureId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Salary structure not found: " + structureId));

        List<SalaryStructureComponent> comps = structureComponentRepository.findBySalaryStructureIdOrderByDisplayOrderAsc(structure.getId());
        List<SalaryStructureDto.ComponentItemDto> compDtos = comps.stream().map(c -> SalaryStructureDto.ComponentItemDto.builder()
                .componentId(c.getSalaryComponent().getId())
                .componentCode(c.getSalaryComponent().getCode())
                .componentName(c.getSalaryComponent().getName())
                .componentType(c.getSalaryComponent().getType())
                .defaultAmount(c.getDefaultAmount())
                .percentageRate(c.getPercentageRate())
                .displayOrder(c.getDisplayOrder())
                .build()).collect(Collectors.toList());

        return SalaryStructureDto.builder()
                .id(structure.getId())
                .name(structure.getName())
                .code(structure.getCode())
                .description(structure.getDescription())
                .isActive(structure.getIsActive())
                .components(compDtos)
                .build();
    }

    public Page<SalaryStructureDto> getStructures(Pageable pageable) {
        UUID tenantId = getRequiredTenantId();
        return structureRepository.findByTenantId(tenantId, pageable)
                .map(s -> SalaryStructureDto.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .code(s.getCode())
                        .description(s.getDescription())
                        .isActive(s.getIsActive())
                        .build());
    }

    // --- Employee Salary Assignment & Revisions ---

    @Transactional
    public EmployeeSalaryStructureDto assignSalaryStructure(EmployeeSalaryStructureDto dto, UUID currentUserId) {
        UUID tenantId = getRequiredTenantId();
        Employee employee = employeeRepository.findByIdAndTenantId(dto.getEmployeeId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + dto.getEmployeeId()));
        SalaryStructure structure = structureRepository.findByIdAndTenantId(dto.getSalaryStructureId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Salary structure not found: " + dto.getSalaryStructureId()));

        if (dto.getEffectiveTo() != null && dto.getEffectiveTo().isBefore(dto.getEffectiveFrom())) {
            throw new IllegalArgumentException("effectiveTo must be on or after effectiveFrom");
        }

        // Check overlap
        boolean overlap = employeeSalaryStructureRepository.hasOverlappingAssignment(
                tenantId, employee.getId(), dto.getEffectiveFrom(), dto.getEffectiveTo(), null
        );
        if (overlap) {
            throw new IllegalArgumentException("Salary structure assignment overlaps with an existing assignment for employee " + employee.getEmployeeCode());
        }

        Integer nextVersion = employeeSalaryStructureRepository.findLatestVersion(tenantId, employee.getId()) + 1;

        // Pro-rate monthly numbers from annual CTC
        BigDecimal monthlyGross = dto.getAnnualCtc().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        BigDecimal monthlyBase = dto.getMonthlyBaseSalary() != null 
                ? dto.getMonthlyBaseSalary() 
                : monthlyGross.multiply(BigDecimal.valueOf(0.50)).setScale(2, RoundingMode.HALF_UP);

        // Previous assignment for revision history
        List<EmployeeSalaryStructure> currentAssignments = employeeSalaryStructureRepository.findActiveAssignmentsAsOf(
                tenantId, employee.getId(), dto.getEffectiveFrom()
        );
        SalaryStructure previousStructure = currentAssignments.isEmpty() ? null : currentAssignments.get(0).getSalaryStructure();
        BigDecimal previousCtc = currentAssignments.isEmpty() ? null : currentAssignments.get(0).getAnnualCtc();

        // Build assignment entity
EmployeeSalaryStructure assignment = EmployeeSalaryStructure.builder()
        .employee(employee)
        .salaryStructure(structure)
        .version(nextVersion)
        .effectiveFrom(dto.getEffectiveFrom())
        .effectiveTo(dto.getEffectiveTo())
        .annualCtc(dto.getAnnualCtc())
        .monthlyGross(monthlyGross)
        .monthlyBaseSalary(monthlyBase)
        .isActive(true)
        .build();

assignment.setTenantId(tenantId);

// Save assignment
assignment = employeeSalaryStructureRepository.saveAndFlush(assignment);

// Record revision history
AppUser revisedBy = currentUserId != null
        ? appUserRepository.findById(currentUserId).orElse(null)
        : null;

SalaryRevisionHistory revision = SalaryRevisionHistory.builder()
        .employee(employee)
        .previousStructure(previousStructure)
        .newStructure(structure)
        .previousAnnualCtc(previousCtc)
        .newAnnualCtc(dto.getAnnualCtc())
        .revisionDate(dto.getEffectiveFrom())
        .revisionReason(dto.getRevisionReason() != null
        ? dto.getRevisionReason()
        : "Salary assignment / revision")
        .revisedBy(revisedBy)
        .build();

revision.setTenantId(tenantId);
revisionHistoryRepository.save(revision);

return toEmployeeSalaryStructureDto(assignment);
    }

    public List<EmployeeSalaryStructureDto> getEmployeeSalaryHistory(UUID employeeId) {
        UUID tenantId = getRequiredTenantId();
        return employeeSalaryStructureRepository.findByTenantIdAndEmployeeIdOrderByEffectiveFromDesc(tenantId, employeeId)
                .stream()
                .map(this::toEmployeeSalaryStructureDto)
                .collect(Collectors.toList());
    }

    public List<SalaryRevisionDto> getEmployeeRevisionHistory(UUID employeeId) {
        UUID tenantId = getRequiredTenantId();
        return revisionHistoryRepository.findByTenantIdAndEmployeeIdOrderByRevisionDateDesc(tenantId, employeeId)
                .stream()
                .map(r -> SalaryRevisionDto.builder()
                        .id(r.getId())
                        .employeeId(r.getEmployee().getId())
                        .employeeName(r.getEmployee().getFirstName() + " " + r.getEmployee().getLastName())
                        .previousStructureId(r.getPreviousStructure() != null ? r.getPreviousStructure().getId() : null)
                        .previousStructureName(r.getPreviousStructure() != null ? r.getPreviousStructure().getName() : null)
                        .newStructureId(r.getNewStructure().getId())
                        .newStructureName(r.getNewStructure().getName())
                        .previousAnnualCtc(r.getPreviousAnnualCtc())
                        .newAnnualCtc(r.getNewAnnualCtc())
                        .revisionDate(r.getRevisionDate())
                        .revisionReason(r.getRevisionReason())
                        .revisedBy(r.getRevisedBy() != null ? r.getRevisedBy().getId() : null)
                        .build())
                .collect(Collectors.toList());
    }

    private SalaryComponentDto toComponentDto(SalaryComponent c) {
        return SalaryComponentDto.builder()
                .id(c.getId())
                .name(c.getName())
                .code(c.getCode())
                .type(c.getType())
                .calculationType(c.getCalculationType())
                .calculationBase(c.getCalculationBase())
                .percentageRate(c.getPercentageRate())
                .isTaxable(c.getIsTaxable())
                .includeInGross(c.getIncludeInGross())
                .includeInCTC(c.getIncludeInCTC())
                .isStatutory(c.getIsStatutory())
                .isActive(c.getIsActive())
                .build();
    }

    private EmployeeSalaryStructureDto toEmployeeSalaryStructureDto(EmployeeSalaryStructure e) {
        return EmployeeSalaryStructureDto.builder()
                .id(e.getId())
                .employeeId(e.getEmployee().getId())
                .employeeCode(e.getEmployee().getEmployeeCode())
                .employeeName(e.getEmployee().getFirstName() + " " + e.getEmployee().getLastName())
                .salaryStructureId(e.getSalaryStructure().getId())
                .salaryStructureName(e.getSalaryStructure().getName())
                .version(e.getVersion())
                .effectiveFrom(e.getEffectiveFrom())
                .effectiveTo(e.getEffectiveTo())
                .annualCtc(e.getAnnualCtc())
                .monthlyGross(e.getMonthlyGross())
                .monthlyBaseSalary(e.getMonthlyBaseSalary())
                .isActive(e.getIsActive())
                .build();
    }
}

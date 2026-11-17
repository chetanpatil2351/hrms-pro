package com.hrmspro.modules.payroll;

import com.hrmspro.common.context.TenantContext;
import com.hrmspro.modules.employee.Employee;
import com.hrmspro.modules.employee.EmployeeRepository;
import com.hrmspro.modules.employee.dto.CreateEmployeeRequest;
import com.hrmspro.modules.employee.dto.EmployeeResponseDto;
import com.hrmspro.modules.payroll.PayrollPeriod;
import com.hrmspro.modules.payroll.PayrollRunRepository;
import com.hrmspro.modules.payroll.dto.PayrollPeriodDto;
import com.hrmspro.modules.tenant.TenantService;
import com.hrmspro.modules.tenant.dto.CreateTenantRequest;
import com.hrmspro.modules.tenant.dto.TenantResponseDto;
import com.hrmspro.modules.user.AppUserRepository;
import com.hrmspro.modules.auth.dto.RegisterRequest;
import com.hrmspro.modules.auth.dto.UserProfileDto;
import com.hrmspro.modules.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.hrmspro.modules.payroll.dto.SalaryComponentDto;
import com.hrmspro.modules.payroll.dto.SalaryStructureDto;
import com.hrmspro.modules.payroll.dto.PayrollPeriodDto;
import com.hrmspro.modules.payroll.dto.PayrollPreviewDto;
import com.hrmspro.modules.payroll.PayrollPeriod;
import com.hrmspro.modules.payroll.PayrollRunRepository;
import com.hrmspro.modules.employee.dto.EmployeeResponseDto;
import com.hrmspro.modules.employee.dto.CreateEmployeeRequest;
import com.hrmspro.modules.employee.EmployeeService;
import com.hrmspro.common.exception.ResourceNotFoundException;
import com.hrmspro.modules.payroll.PayrollRunRepository;

import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class PayrollPreviewServiceTests {

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private PayrollRunRepository runRepository;

    // No direct period service needed; using PayrollService.createPeriod

    @Autowired
    private SalaryStructureService salaryStructureService;

    @Autowired
    private AuthService authService;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @MockBean
    private PayrollCalculationEngine calculationEngine; // spy the engine

    private UUID tenantId;
    private UUID adminUserId;
    private Employee testEmployee;
    private SalaryStructureDto standardStructure;
    private PayrollPeriodDto testPeriod;

    @BeforeEach
    void setUp() {
        // Register admin user
        String unique = UUID.randomUUID().toString().substring(0, 8);
        UserProfileDto adminUser = authService.register(RegisterRequest.builder()
                .email("admin." + unique + "@preview.com")
                .password("Password@123")
                .firstName("Admin")
                .lastName("Preview")
                .build());
        adminUserId = adminUser.getId();

        // Create tenant
        TenantResponseDto tenant = tenantService.createTenant(adminUserId,
                CreateTenantRequest.builder()
                        .name("Preview Tenant " + unique)
                        .subdomain("preview-" + unique)
                        .companyCode("PVW" + unique)
                        .email("admin." + unique + "@preview.com")
                        .build());
        tenantId = tenant.getId();
        TenantContext.setTenantId(tenantId);

        // Employee created below uses the default branch from tenant onboarding

        // Create employee
        Employee employee = Employee.builder()
                .employeeCode("E001")
                .firstName("John")
                .lastName("Doe")
                .workEmail("john.doe@preview.com")
                .joiningDate(LocalDate.of(2025, 1, 1))
                .employmentStatus("ACTIVE")
                .designation("Software Engineer")
                .build();
        employee.setTenantId(tenantId);
        testEmployee = employeeRepository.save(employee);

        // Salary components
        SalaryComponentDto basicComp = salaryStructureService.createComponent(SalaryComponentDto.builder()
                .name("Basic Salary")
                .code("BASIC")
                .type("EARNING")
                .calculationType("PERCENTAGE")
                .calculationBase("BASIC")
                .percentageRate(BigDecimal.ONE)
                .isTaxable(true)
                .includeInGross(true)
                .includeInCTC(true)
                .build());
        SalaryComponentDto specialComp = salaryStructureService.createComponent(SalaryComponentDto.builder()
                .name("Special Allowance")
                .code("SPECIAL")
                .type("EARNING")
                .calculationType("FIXED_AMOUNT")
                .isTaxable(true)
                .includeInGross(true)
                .includeInCTC(true)
                .build());

        // Salary structure
        standardStructure = salaryStructureService.createStructure(SalaryStructureDto.builder()
                .name("Standard")
                .code("STD")
                .components(List.of(
                        SalaryStructureDto.ComponentItemDto.builder()
                                .componentId(basicComp.getId())
                                .percentageRate(BigDecimal.ONE)
                                .displayOrder(1)
                                .build(),
                        SalaryStructureDto.ComponentItemDto.builder()
                                .componentId(specialComp.getId())
                                .defaultAmount(BigDecimal.valueOf(5000))
                                .displayOrder(2)
                                .build()))
                .build());

        // Create a draft payroll period
        testPeriod = payrollService.createPeriod(PayrollPeriodDto.builder()
                .year(2026)
                .month(9)
                .build());
    }

    @Test
    @DisplayName("Preview calculates employee payroll and does not persist any run entities")
    void previewCalculatesAndIsReadOnly() {
        // Mock calculation result
        PayrollCalculationEngine.CalculationResult mockResult = mock(PayrollCalculationEngine.CalculationResult.class);
        when(mockResult.getLineItems()).thenReturn(List.of());
        when(mockResult.getTotalDaysInMonth()).thenReturn(30);
        when(mockResult.getPayableDays()).thenReturn(BigDecimal.valueOf(30));
        when(mockResult.getUnpaidLeaveDays()).thenReturn(BigDecimal.valueOf(0));
        when(mockResult.getGrossEarnings()).thenReturn(BigDecimal.valueOf(6000));
        when(mockResult.getTotalDeductions()).thenReturn(BigDecimal.valueOf(500));
        when(mockResult.getEmployerContributions()).thenReturn(BigDecimal.valueOf(1200));
        when(mockResult.getNetSalary()).thenReturn(BigDecimal.valueOf(5500));
        when(calculationEngine.calculateEmployeePayroll(eq(tenantId), any(Employee.class), any(PayrollPeriod.class)))
                .thenReturn(mockResult);

        PayrollPreviewDto preview = payrollService.previewPayrollRun(testPeriod.getId());

        // Verify calculation engine called once per employee (only one employee in setup)
        verify(calculationEngine, times(1)).calculateEmployeePayroll(eq(tenantId), any(Employee.class), any(PayrollPeriod.class));

        // Verify preview contents
        assertThat(preview.getProcessedEmployees()).isEqualTo(1);
        assertThat(preview.getTotalGross()).isEqualByComparingTo(BigDecimal.valueOf(6000));
        assertThat(preview.getTotalNet()).isEqualByComparingTo(BigDecimal.valueOf(5500));
        assertThat(preview.getEmployeePayrollSummaries()).hasSize(1);

        // Ensure no PayrollRun was persisted
        assertThat(runRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Tenant isolation - cannot preview period belonging to another tenant")
    void tenantIsolationPreviewFails() {
        // Create a second tenant and period
        String unique = UUID.randomUUID().toString().substring(0, 4);
        UserProfileDto otherAdmin = authService.register(RegisterRequest.builder()
                .email("admin2." + unique + "@test.com")
                .password("Password@123")
                .firstName("Admin2")
                .lastName("Other")
                .build());
        UUID otherUserId = otherAdmin.getId();
        TenantResponseDto otherTenant = tenantService.createTenant(otherUserId,
                CreateTenantRequest.builder()
                        .name("Other Tenant " + unique)
                        .subdomain("other-" + unique)
                        .companyCode("OTH" + unique)
                        .email("admin2." + unique + "@test.com")
                        .build());
        UUID otherTenantId = otherTenant.getId();
        TenantContext.setTenantId(otherTenantId);
        PayrollPeriodDto otherPeriod = payrollService.createPeriod(PayrollPeriodDto.builder()
                .year(2026)
                .month(9)
                .build());

        // Switch back to original tenant and attempt preview of other tenant's period
        TenantContext.setTenantId(tenantId);
        assertThrows(ResourceNotFoundException.class, () -> payrollService.previewPayrollRun(otherPeriod.getId()));
    }
}

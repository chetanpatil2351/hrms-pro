package com.hrmspro.modules.payroll;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrmspro.common.context.TenantContext;
import com.hrmspro.modules.auth.dto.RegisterRequest;
import com.hrmspro.modules.auth.dto.UserProfileDto;
import com.hrmspro.modules.auth.service.AuthService;
import com.hrmspro.modules.employee.Employee;
import com.hrmspro.modules.employee.EmployeeRepository;
import com.hrmspro.modules.employee.dto.CreateEmployeeRequest;
import com.hrmspro.modules.employee.dto.EmployeeResponseDto;
import com.hrmspro.modules.payroll.dto.PayrollPeriodDto;
import com.hrmspro.modules.tenant.TenantService;
import com.hrmspro.modules.tenant.dto.BranchDto;
import com.hrmspro.modules.tenant.dto.CreateTenantRequest;
import com.hrmspro.modules.tenant.dto.TenantResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.TestConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class PayrollSerializationRollbackTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PayrollRunRepository runRepository;

    private UUID adminUserId;
    private UUID tenantId;
    private Employee testEmployee;

    @BeforeEach
    void setUp() {
        // 1. Register admin user (no salary structure will be assigned)
        String unique = UUID.randomUUID().toString().substring(0, 8);
        UserProfileDto adminUser = authService.register(RegisterRequest.builder()
                .email("admin." + unique + "@acme.com")
                .password("Password@123")
                .firstName("Admin")
                .lastName("Payroll")
                .build());
        adminUserId = adminUser.getId();

        // 2. Create tenant
        TenantResponseDto tenant = tenantService.createTenant(adminUserId,
                CreateTenantRequest.builder()
                        .name("Acme Payroll " + unique)
                        .subdomain("acme-pay-" + unique)
                        .companyCode("ACME" + unique)
                        .email("admin." + unique + "@acme.com")
                        .build());
        tenantId = tenant.getId();
        TenantContext.setTenantId(tenantId);

        // 3. Create a branch (required for tenant)
        tenantService.createBranch(tenantId, BranchDto.builder()
                .name("HQ Branch")
                .code("HQ-" + unique)
                .build());

        // 4. Create an employee – we will NOT assign a salary structure, so calculation will fail
        EmployeeResponseDto empDto = authService.register(RegisterRequest.builder()
                .email("dummy." + unique + "@acme.com")
                .password("Password@123")
                .firstName("John")
                .lastName("Doe")
                .build()); // using authService just to get a user; create employee via repository directly
        // For simplicity, directly persist employee with minimal required fields
        Employee emp = new Employee();
        emp.setTenantId(tenantId);
        emp.setEmployeeCode("E001");
        emp.setFirstName("John");
        emp.setLastName("Doe");
        emp.setEmploymentStatus("ACTIVE");
        emp.setJoiningDate(java.time.LocalDate.of(2026, 1, 1));
        emp = employeeRepository.save(emp);
        testEmployee = emp;
    }

    @Test
    @DisplayName("Serialization failure should roll back entire payroll transaction")
    void testSerializationRollback() {
        // Create a payroll period (no salary structure for employee => calculation will fail => failure list non‑empty)
        PayrollPeriodDto period = payrollService.createPeriod(PayrollPeriodDto.builder()
                .year(2026)
                .month(1)
                .build());

        // Expect JsonProcessingException due to faulty ObjectMapper (provided by TestConfiguration below)
        assertThrows(JsonProcessingException.class, () -> {
            payrollService.calculatePayrollRun(period.getId(), adminUserId);
        });

        // Verify that no PayrollRun was persisted for this period (transaction rolled back)
        assertThat(runRepository.findByPayrollPeriodIdOrderByRunNumberDesc(period.getId()))
                .isEmpty();
    }

    @TestConfiguration
    static class FaultyObjectMapperConfig {
        @Bean
        @Primary
        public ObjectMapper objectMapper() {
            return new ObjectMapper() {
                @Override
                public String writeValueAsString(Object value) throws JsonProcessingException {
                    // Force a serialization failure regardless of input
                    throw new JsonProcessingException("forced serialization failure") {};
                }
            };
        }
    }
}

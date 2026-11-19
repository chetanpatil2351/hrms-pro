package com.hrmspro.modules.payroll;

import com.hrmspro.modules.auth.dto.RegisterRequest;
import com.hrmspro.modules.auth.dto.UserProfileDto;
import com.hrmspro.modules.auth.service.AuthService;
import com.hrmspro.modules.tenant.TenantService;
import com.hrmspro.modules.tenant.dto.BranchDto;
import com.hrmspro.modules.tenant.dto.CreateTenantRequest;
import com.hrmspro.modules.tenant.dto.TenantResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class PayrollAllFailureSafetyTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private PayrollService payrollService;

    private UUID adminUserId;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        // Register an admin user (no employees will be created)
        String unique = UUID.randomUUID().toString().substring(0, 8);
        UserProfileDto adminUser = authService.register(RegisterRequest.builder()
                .email("admin." + unique + "@acme.com")
                .password("Password@123")
                .firstName("Admin")
                .lastName("Payroll")
                .build());
        adminUserId = adminUser.getId();

        // Create a tenant
        TenantResponseDto tenant = tenantService.createTenant(adminUserId,
                CreateTenantRequest.builder()
                        .name("Acme Payroll " + unique)
                        .subdomain("acme-pay-" + unique)
                        .companyCode("ACME" + unique)
                        .email("admin." + unique + "@acme.com")
                        .build());
        tenantId = tenant.getId();
        // Set tenant context (the service reads from TenantContext static holder)
        com.hrmspro.common.context.TenantContext.setTenantId(tenantId);

        // Create a branch (required for tenant setup, though not used here)
        tenantService.createBranch(tenantId, BranchDto.builder()
                .name("HQ Branch")
                .code("HQ-" + unique)
                .build());
    }

    @Test
    @DisplayName("Approval must be rejected when payroll run processed zero employees")
    void testApproveRejectedForAllFailureRun() {
        // Create a payroll period – there are no employees in the system
        PayrollPeriodDto period = payrollService.createPeriod(PayrollPeriodDto.builder()
                .year(2026)
                .month(1)
                .build());

        // Run calculation – should succeed with zero processed employees
        payrollService.calculatePayrollRun(period.getId(), adminUserId);

        // Attempt to approve – expect IllegalStateException
        assertThrows(IllegalStateException.class, () -> payrollService.approvePeriod(period.getId()));
    }

    @Test
    @DisplayName("Finalization must be rejected when payroll run processed zero employees")
    void testFinalizeRejectedForAllFailureRun() {
        // Create a payroll period – no employees
        PayrollPeriodDto period = payrollService.createPeriod(PayrollPeriodDto.builder()
                .year(2026)
                .month(2)
                .build());

        // Run calculation – zero employees
        payrollService.calculatePayrollRun(period.getId(), adminUserId);

        // Manually set period status to APPROVED to reach finalization logic (simulating a bypass)
        // Since approvePeriod would reject, we set status directly for test purposes.
        // Retrieve the period, change status, and save via repository (using service private method is not possible).
        // Use reflection to access periodRepository.
        com.hrmspro.modules.payroll.PayrollPeriod periodEntity =
                payrollService.periodRepository.findByIdAndTenantId(period.getId(), tenantId).orElseThrow();
        periodEntity.setStatus("APPROVED");
        payrollService.periodRepository.save(periodEntity);

        // Attempt to finalize – expect IllegalStateException because no employees were processed
        assertThrows(IllegalStateException.class, () -> payrollService.finalizePeriod(period.getId(), adminUserId));
    }
}

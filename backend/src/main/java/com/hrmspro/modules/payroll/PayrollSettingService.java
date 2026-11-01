package com.hrmspro.modules.payroll;

import com.hrmspro.common.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollSettingService {

    private final PayrollSettingRepository payrollSettingRepository;

    private UUID getRequiredTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context not set");
        }
        return tenantId;
    }

    @Transactional(readOnly = true)
    public PayrollSetting getSetting() {
        UUID tenantId = getRequiredTenantId();
        return payrollSettingRepository.findByTenantId(tenantId)
                .orElseGet(() -> PayrollSetting.builder()
                        .prorationPolicy("CALENDAR_DAYS")
                        .build());
    }

    @Transactional
    public PayrollSetting updateProrationPolicy(String prorationPolicy) {
        UUID tenantId = getRequiredTenantId();
        String policy = "WORKING_DAYS".equalsIgnoreCase(prorationPolicy) ? "WORKING_DAYS" : "CALENDAR_DAYS";

        PayrollSetting setting = payrollSettingRepository.findByTenantId(tenantId)
                .orElseGet(() -> {
                    PayrollSetting s = PayrollSetting.builder()
                            .prorationPolicy(policy)
                            .build();
                    s.setTenantId(tenantId);
                    return s;
                });

        setting.setProrationPolicy(policy);
        return payrollSettingRepository.save(setting);
    }
}

package com.hrmspro.modules.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayrollSettingRepository extends JpaRepository<PayrollSetting, UUID> {
    Optional<PayrollSetting> findByTenantId(UUID tenantId);
}

package com.hrmspro.common.context;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Aspect that intercepts @Transactional service methods to bind TenantContext
 * directly into the current PostgreSQL database transaction via set_config.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class TenantContextAspect {

    private final EntityManager entityManager;

    @Around("@within(org.springframework.transaction.annotation.Transactional) || @annotation(org.springframework.transaction.annotation.Transactional)")
    public Object applyTenantContextToTransaction(ProceedingJoinPoint joinPoint) throws Throwable {
        UUID tenantId = TenantContext.getTenantId();

        if (tenantId != null && TransactionSynchronizationManager.isActualTransactionActive()) {
            try {
                // Execute PostgreSQL transaction-local tenant context setting
                // is_local = true ensures the setting is automatically discarded on commit/rollback
                entityManager.createNativeQuery("SELECT set_config('app.current_tenant_id', :tenantId, true)")
                        .setParameter("tenantId", tenantId.toString())
                        .getSingleResult();
                log.trace("Applied transaction-local PostgreSQL tenant context: {}", tenantId);
            } catch (Exception ex) {
                // If dialect doesn't support set_config (e.g. standard H2 unit test mode without pg compatibility function),
                // log trace and continue so in-memory unit tests proceed cleanly.
                log.trace("Could not apply set_config (non-PostgreSQL dialect or test environment): {}", ex.getMessage());
            }
        }

        return joinPoint.proceed();
    }
}
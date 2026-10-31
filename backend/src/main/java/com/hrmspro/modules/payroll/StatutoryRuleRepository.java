package com.hrmspro.modules.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StatutoryRuleRepository extends JpaRepository<StatutoryRule, UUID> {
    Optional<StatutoryRule> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("""
        SELECT s FROM StatutoryRule s
        WHERE s.tenantId = :tenantId
          AND s.ruleType = :ruleType
          AND (:state IS NULL OR s.state = :state)
          AND s.effectiveYear <= :year
          AND s.isActive = true
        ORDER BY s.effectiveYear DESC
    """)
    List<StatutoryRule> findApplicableRules(
            @Param("tenantId") UUID tenantId,
            @Param("ruleType") String ruleType,
            @Param("state") String state,
            @Param("year") Integer year
    );

    List<StatutoryRule> findByTenantIdAndEffectiveYear(UUID tenantId, Integer effectiveYear);
}

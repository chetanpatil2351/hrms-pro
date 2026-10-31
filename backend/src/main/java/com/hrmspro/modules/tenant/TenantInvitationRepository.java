package com.hrmspro.modules.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantInvitationRepository extends JpaRepository<TenantInvitation, UUID> {
    Optional<TenantInvitation> findByTokenHash(String tokenHash);
    List<TenantInvitation> findByTenantIdAndStatus(UUID tenantId, String status);
    List<TenantInvitation> findByTenantId(UUID tenantId);
    boolean existsByTenantIdAndEmailAndStatus(UUID tenantId, String email, String status);
    long countByTenantId(UUID tenantId);
}
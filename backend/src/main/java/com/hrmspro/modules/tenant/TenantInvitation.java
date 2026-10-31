package com.hrmspro.modules.tenant;

import com.hrmspro.common.base.BaseTenantEntity;
import com.hrmspro.modules.rbac.Role;
import com.hrmspro.modules.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "tenant_invitations")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantInvitation extends BaseTenantEntity {

    @Column(nullable = false)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_by", nullable = false)
    private AppUser invitedBy;

    @Column(length = 150)
    private String designation;

    @Column(name = "employee_code", length = 50)
    private String employeeCode;

    @Builder.Default
    @Column(nullable = false, length = 50)
    private String status = "PENDING"; // PENDING, ACCEPTED, EXPIRED, REVOKED

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;
}
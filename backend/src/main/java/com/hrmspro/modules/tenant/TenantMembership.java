package com.hrmspro.modules.tenant;

import com.hrmspro.common.base.BaseTenantEntity;
import com.hrmspro.modules.rbac.Role;
import com.hrmspro.modules.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(
    name = "tenant_memberships",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_tenant_membership", columnNames = {"user_id", "tenant_id"})
    }
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantMembership extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Builder.Default
    @Column(nullable = false, length = 50)
    private String status = "ACTIVE"; // INVITED, ACTIVE, SUSPENDED, REVOKED

    @Column(length = 150)
    private String designation;

    @Column(name = "employee_code", length = 50)
    private String employeeCode;

    @Column(name = "joined_at")
    private Instant joinedAt;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "membership_roles",
        joinColumns = @JoinColumn(name = "membership_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();
}
package com.hrmspro.modules.tenant;

import com.hrmspro.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Getter
@Setter
@Entity
@Table(name = "tenants")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String subdomain;

    @Column(name = "company_code", nullable = false, unique = true, length = 50)
    private String companyCode;

    @Column(nullable = false)
    private String email;

    @Column(length = 50)
    private String phone;

    @Builder.Default
    @Column(name = "subscription_plan", nullable = false, length = 50)
    private String subscriptionPlan = "FREE_TRIAL";

    @Builder.Default
    @Column(name = "max_employees", nullable = false)
    private Integer maxEmployees = 10;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
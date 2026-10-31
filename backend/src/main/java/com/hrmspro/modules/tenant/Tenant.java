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

    @Column(name = "legal_name")
    private String legalName;

    @Column(nullable = false, unique = true, length = 100)
    private String subdomain;

    @Column(name = "company_code", nullable = false, unique = true, length = 50)
    private String companyCode;

    @Column(nullable = false)
    private String email;

    @Column(length = 50)
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Builder.Default
    @Column(length = 100)
    private String country = "India";

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(length = 100)
    private String industry;

    private String website;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Builder.Default
    @Column(length = 50)
    private String timezone = "Asia/Kolkata";

    @Builder.Default
    @Column(name = "subscription_plan", nullable = false, length = 50)
    private String subscriptionPlan = "FREE_TRIAL";

    @Builder.Default
    @Column(name = "subscription_status", length = 50)
    private String subscriptionStatus = "TRIAL"; // TRIAL, ACTIVE, EXPIRED, SUSPENDED

    @Builder.Default
    @Column(name = "max_employees", nullable = false)
    private Integer maxEmployees = 10;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
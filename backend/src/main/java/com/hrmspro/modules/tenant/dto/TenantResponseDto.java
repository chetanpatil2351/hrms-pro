package com.hrmspro.modules.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantResponseDto {
    private UUID id;
    private String name;
    private String legalName;
    private String subdomain;
    private String companyCode;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private String industry;
    private String website;
    private String logoUrl;
    private String timezone;
    private String subscriptionPlan;
    private String subscriptionStatus;
    private int maxEmployees;
    private long currentEmployeeCount;
    private boolean active;
    private Instant createdAt;
}
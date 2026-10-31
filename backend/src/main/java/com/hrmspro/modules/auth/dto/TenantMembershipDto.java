package com.hrmspro.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantMembershipDto {
    private UUID membershipId;
    private UUID tenantId;
    private String tenantName;
    private String tenantSubdomain;
    private String companyCode;
    private String status;
    private String designation;
    private String employeeCode;
    private List<String> roles;
}
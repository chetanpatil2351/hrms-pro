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
public class InvitationResponseDto {
    private UUID id;
    private UUID tenantId;
    private String email;
    private String roleName;
    private String branchName;
    private String departmentName;
    private String designation;
    private String employeeCode;
    private String status;
    private Instant expiresAt;
    private String rawToken; // populated only upon initial creation response
}
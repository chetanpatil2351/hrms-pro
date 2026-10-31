package com.hrmspro.modules.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwitchTenantRequest {

    @NotNull(message = "Target tenant ID is required")
    private UUID tenantId;
}
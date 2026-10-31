package com.hrmspro.modules.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchDto {
    private UUID id;

    @NotBlank(message = "Branch name is required")
    private String name;

    @NotBlank(message = "Branch code is required")
    private String code;

    private String address;
    private String city;
    private String state;
    private String country;
    private Boolean active;
}
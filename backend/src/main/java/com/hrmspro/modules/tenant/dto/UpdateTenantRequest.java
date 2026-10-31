package com.hrmspro.modules.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTenantRequest {

    @NotBlank(message = "Company name is required")
    private String name;

    private String legalName;

    @NotBlank(message = "Company email is required")
    @Email(message = "Invalid email format")
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
}
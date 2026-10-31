package com.hrmspro.modules.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTenantRequest {

    @NotBlank(message = "Company name is required")
    private String name;

    private String legalName;

    @NotBlank(message = "Subdomain is required")
    @Size(min = 3, max = 50, message = "Subdomain must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Subdomain can only contain lowercase alphanumeric characters and hyphens")
    private String subdomain;

    @NotBlank(message = "Company code is required")
    @Size(min = 2, max = 10, message = "Company code must be between 2 and 10 characters")
    @Pattern(regexp = "^[A-Z0-9]+$", message = "Company code must be uppercase alphanumeric")
    private String companyCode;

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
    private String timezone;
}
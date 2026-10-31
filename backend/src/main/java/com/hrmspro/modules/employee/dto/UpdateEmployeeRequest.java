package com.hrmspro.modules.employee.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEmployeeRequest {

    @NotBlank(message = "First name is required")
    private String firstName;

    private String middleName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    private LocalDate dateOfBirth;
    private String gender;

    @Email(message = "Invalid personal email format")
    private String personalEmail;

    @Email(message = "Invalid work email format")
    private String workEmail;

    private String phone;
    private String address;
    private String profilePhotoUrl;

    private String employmentType;
    private String designation;
    private UUID branchId;
    private UUID departmentId;
    private UUID reportingManagerId;
    private LocalDate probationEndDate;

    private String emergencyContactName;
    private String emergencyContactRelation;
    private String emergencyContactPhone;
}
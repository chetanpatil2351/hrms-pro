package com.hrmspro.modules.employee.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeResponseDto {
    private UUID id;
    private UUID tenantId;
    private UUID userId;
    private UUID membershipId;
    private String employeeCode;

    private String firstName;
    private String middleName;
    private String lastName;
    private String fullName;
    private LocalDate dateOfBirth;
    private String gender;

    private String personalEmail;
    private String workEmail;
    private String phone;
    private String address;
    private String profilePhotoUrl;

    private LocalDate joiningDate;
    private String employmentType;
    private String designation;
    private String employmentStatus;
    private LocalDate probationEndDate;

    private UUID branchId;
    private String branchName;
    private UUID departmentId;
    private String departmentName;

    private UUID reportingManagerId;
    private String reportingManagerName;

    private String emergencyContactName;
    private String emergencyContactRelation;
    private String emergencyContactPhone;

    // Masked Identity values
    private String panNumberMasked;
    private String aadhaarNumberMasked;

    private Instant createdAt;
    private Instant updatedAt;
}
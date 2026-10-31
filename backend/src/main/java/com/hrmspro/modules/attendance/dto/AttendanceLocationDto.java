package com.hrmspro.modules.attendance.dto;

import jakarta.validation.constraints.NotBlank;
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
public class AttendanceLocationDto {
    private UUID id;

    @NotNull(message = "Branch ID is required")
    private UUID branchId;
    private String branchName;

    @NotBlank(message = "Location name is required")
    private String name;

    @NotNull(message = "Latitude is required")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    private Double longitude;

    private Integer radiusMeters;
    private String policyMode; // STRICT, WARNING_ONLY, APPROVAL_REQUIRED
    private Boolean active;
}
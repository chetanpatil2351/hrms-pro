package com.hrmspro.modules.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewFraudFlagRequest {

    @NotBlank(message = "Status is required")
    @Pattern(regexp = "UNDER_REVIEW|RESOLVED|DISMISSED", message = "Status must be UNDER_REVIEW, RESOLVED, or DISMISSED")
    private String status;

    private String remarks;
}

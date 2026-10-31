package com.hrmspro.modules.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckOutRequest {
    private String idempotencyKey;
    private Double latitude;
    private Double longitude;
    private Double accuracyMeters;
    private Boolean isMockLocation;
    private UUID locationId;

    private String selfieFilePath;
    private String selfieMimeType;

    private String deviceInfo;
    private String ipAddress;
}
package com.hrmspro.modules.employee.dto;

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
public class EmployeeAuditLogDto {
    private UUID id;
    private UUID employeeId;
    private UUID actorId;
    private String action;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private String details;
    private Instant createdAt;
}
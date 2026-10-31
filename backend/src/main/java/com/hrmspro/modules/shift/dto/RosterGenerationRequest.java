package com.hrmspro.modules.shift.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class RosterGenerationRequest {

    private LocalDate fromDate;
    private LocalDate toDate;         // max 31 days span enforced in service
    private List<UUID> employeeIds;   // null = all active employees in scope
    private UUID branchId;            // null = entire tenant
    private UUID departmentId;        // null = all departments
    private boolean overwriteDrafts;  // if true, replace existing DRAFT rosters
}

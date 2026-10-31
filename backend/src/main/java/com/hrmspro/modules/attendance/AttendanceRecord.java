package com.hrmspro.modules.attendance;

import com.hrmspro.common.base.BaseTenantEntity;
import com.hrmspro.modules.employee.Employee;
import com.hrmspro.modules.tenant.Branch;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "attendance_records")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceRecord extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "check_in_time", nullable = false)
    private Instant checkInTime;

    @Column(name = "check_out_time")
    private Instant checkOutTime;

    @Builder.Default
    @Column(nullable = false, length = 50)
    private String status = "IN_PROGRESS"; // IN_PROGRESS, PRESENT, HALF_DAY, OVERTIME, INCOMPLETE, ON_LEAVE

    @Builder.Default
    @Column(name = "work_duration_minutes")
    private Integer workDurationMinutes = 0;

    @Builder.Default
    @Column(name = "break_duration_minutes")
    private Integer breakDurationMinutes = 0;

    @Builder.Default
    @Column(name = "net_work_duration_minutes")
    private Integer netWorkDurationMinutes = 0;

    @Builder.Default
    @Column(name = "is_late", nullable = false)
    private Boolean isLate = false;

    @Builder.Default
    @Column(name = "is_early_departure", nullable = false)
    private Boolean isEarlyDeparture = false;

    @Builder.Default
    @Column(name = "is_geofence_verified", nullable = false)
    private Boolean isGeofenceVerified = true;

    @Builder.Default
    @Column(name = "is_selfie_verified", nullable = false)
    private Boolean isSelfieVerified = false;

    @Builder.Default
    @OneToMany(mappedBy = "attendanceRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AttendanceEvent> events = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "attendanceRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AttendanceBreak> breaks = new ArrayList<>();
}
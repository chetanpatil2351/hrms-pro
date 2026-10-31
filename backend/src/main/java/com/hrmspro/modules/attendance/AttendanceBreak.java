package com.hrmspro.modules.attendance;

import com.hrmspro.common.base.BaseTenantEntity;
import com.hrmspro.modules.employee.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "attendance_breaks")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceBreak extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attendance_record_id", nullable = false)
    private AttendanceRecord attendanceRecord;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Builder.Default
    @Column(name = "break_type", nullable = false, length = 50)
    private String breakType = "GENERAL"; // LUNCH, TEA, BIO, GENERAL

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;
}
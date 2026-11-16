package com.hrmspro.modules.attendance;

import com.hrmspro.common.base.BaseTenantEntity;
import com.hrmspro.modules.employee.Employee;
import com.hrmspro.modules.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "attendance_fraud_flags")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceFraudFlag extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_record_id")
    private AttendanceRecord attendanceRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_event_id")
    private AttendanceEvent attendanceEvent;

    @Column(name = "flag_type", nullable = false, length = 100)
    private String flagType; // RAPID_PUNCH, SUSPICIOUS_JUMP, IMPOSSIBLE_TRAVEL, REPEATED_GEOFENCE_FAILURE, REPEATED_LOW_ACCURACY, MOCK_LOCATION_REPORTED

    @Builder.Default
    @Column(nullable = false, length = 50)
    private String severity = "MEDIUM"; // LOW, MEDIUM, HIGH, CRITICAL

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Builder.Default
    @Column(nullable = false, length = 50)
    private String status = "FLAGGED"; // FLAGGED, UNDER_REVIEW, RESOLVED, DISMISSED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private AppUser reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_remarks", columnDefinition = "TEXT")
    private String reviewRemarks;
}

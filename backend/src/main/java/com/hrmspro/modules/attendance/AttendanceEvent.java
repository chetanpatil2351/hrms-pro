package com.hrmspro.modules.attendance;

import com.hrmspro.common.base.BaseTenantEntity;
import com.hrmspro.modules.employee.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "attendance_events")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceEvent extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attendance_record_id", nullable = false)
    private AttendanceRecord attendanceRecord;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType; // CHECK_IN, CHECK_OUT

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    private Double latitude;
    private Double longitude;

    @Column(name = "accuracy_meters")
    private Double accuracyMeters;

    @Column(name = "distance_meters")
    private Double distanceMeters;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "geofence_location_id")
    private AttendanceLocation geofenceLocation;

    @Builder.Default
    @Column(name = "geofence_status", length = 50)
    private String geofenceStatus = "NOT_APPLICABLE"; // VERIFIED, OUT_OF_BOUNDS, WARNING, EXCEPTION

    @Builder.Default
    @Column(name = "is_mock_location")
    private Boolean isMockLocation = false;

    @Column(name = "selfie_file_path", length = 500)
    private String selfieFilePath;

    @Column(name = "selfie_mime_type", length = 100)
    private String selfieMimeType;

    @Builder.Default
    @Column(name = "selfie_verified")
    private Boolean selfieVerified = false;

    @Column(name = "device_info", columnDefinition = "TEXT")
    private String deviceInfo;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;
}
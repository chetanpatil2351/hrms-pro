package com.hrmspro.modules.leave;

import com.hrmspro.common.base.BaseTenantEntity;
import com.hrmspro.modules.employee.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "leave_applications")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveApplication extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Builder.Default
    @Column(name = "is_half_day", nullable = false)
    private Boolean isHalfDay = false;

    @Column(name = "half_day_session", length = 20)
    private String halfDaySession; // FIRST_HALF, SECOND_HALF

    @Column(name = "total_days", nullable = false, precision = 4, scale = 2)
    private BigDecimal totalDays;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "attachment_url", columnDefinition = "TEXT")
    private String attachmentUrl;

    @Builder.Default
    @Column(nullable = false, length = 50)
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED, CANCELLED, WITHDRAWN

    @Column(name = "policy_snapshot", columnDefinition = "TEXT")
    private String policySnapshot;
}

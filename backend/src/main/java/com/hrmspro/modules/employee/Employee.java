package com.hrmspro.modules.employee;

import com.hrmspro.common.base.BaseTenantEntity;
import com.hrmspro.modules.tenant.Branch;
import com.hrmspro.modules.tenant.Department;
import com.hrmspro.modules.tenant.TenantMembership;
import com.hrmspro.modules.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
    name = "employees",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_tenant_employee_code", columnNames = {"tenant_id", "employee_code"})
    }
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_id")
    private TenantMembership membership;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporting_manager_id")
    private Employee reportingManager;

    @Column(name = "employee_code", nullable = false, length = 50)
    private String employeeCode;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 20)
    private String gender;

    @Column(name = "personal_email")
    private String personalEmail;

    @Column(name = "work_email")
    private String workEmail;

    @Column(length = 50)
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "profile_photo_url", columnDefinition = "TEXT")
    private String profilePhotoUrl;

    @Column(name = "joining_date", nullable = false)
    private LocalDate joiningDate;

    @Builder.Default
    @Column(name = "employment_type", nullable = false, length = 50)
    private String employmentType = "FULL_TIME"; // FULL_TIME, PART_TIME, CONTRACT, INTERN

    @Column(nullable = false, length = 150)
    private String designation;

    @Column(name = "probation_end_date")
    private LocalDate probationEndDate;

    @Builder.Default
    @Column(name = "employment_status", nullable = false, length = 50)
    private String employmentStatus = "ACTIVE"; // ACTIVE, PROBATION, ON_LEAVE, RESIGNED, TERMINATED

    // Emergency Contact
    @Column(name = "emergency_contact_name", length = 150)
    private String emergencyContactName;

    @Column(name = "emergency_contact_relation", length = 50)
    private String emergencyContactRelation;

    @Column(name = "emergency_contact_phone", length = 50)
    private String emergencyContactPhone;

    // Masked Identity Reference
    @Column(name = "pan_number_masked", length = 20)
    private String panNumberMasked;

    @Column(name = "aadhaar_number_masked", length = 20)
    private String aadhaarNumberMasked;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;
}
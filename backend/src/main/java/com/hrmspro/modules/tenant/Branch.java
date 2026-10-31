package com.hrmspro.modules.tenant;

import com.hrmspro.common.base.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Getter
@Setter
@Entity
@Table(name = "branches")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Branch extends BaseTenantEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Builder.Default
    @Column(length = 100)
    private String country = "India";

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
package com.hrmspro.modules.employee;

import com.hrmspro.common.base.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "employee_documents")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeDocument extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType; // RESUME, ID_PROOF, ADDRESS_PROOF, EDUCATION, OFFER_LETTER, CONTRACT, OTHER

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;
}
package com.hrmspro.modules.employee;

import com.hrmspro.common.response.ApiResponse;
import com.hrmspro.config.security.UserPrincipal;
import com.hrmspro.modules.employee.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
@Tag(name = "Employee Management", description = "Endpoints for employee profiles, documents, and onboarding")
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping("/stats")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER') or hasRole('BRANCH_MANAGER') or hasRole('PAYROLL_MANAGER') or hasRole('ACCOUNTANT')")
    @Operation(summary = "Get employee dashboard stats (total, active, inactive, probation, departments, branches)")
    public ResponseEntity<ApiResponse<EmployeeStatsDto>> getEmployeeStats(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        EmployeeStatsDto stats = employeeService.getEmployeeStats(principal.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(stats, "Employee stats retrieved successfully"));
    }

    @PostMapping
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER')")
    @Operation(summary = "Onboard and create new employee record")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> createEmployee(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateEmployeeRequest request
    ) {
        EmployeeResponseDto response = employeeService.createEmployee(principal.getTenantId(), principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Employee record created successfully"));
    }

    @GetMapping
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER') or hasRole('BRANCH_MANAGER') or hasRole('PAYROLL_MANAGER') or hasRole('ACCOUNTANT')")
    @Operation(summary = "List and search employees with pagination and filters")
    public ResponseEntity<ApiResponse<Page<EmployeeResponseDto>>> getEmployees(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String employmentStatus,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<EmployeeResponseDto> response = employeeService.getEmployees(
                principal.getTenantId(),
                branchId,
                departmentId,
                employmentStatus,
                search,
                pageable
        );
        return ResponseEntity.ok(ApiResponse.success(response, "Employees retrieved successfully"));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user's employee profile")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> getMyProfile(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        EmployeeResponseDto response = employeeService.getCurrentEmployeeProfile(principal.getTenantId(), principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response, "Self employee profile retrieved"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER') or hasRole('BRANCH_MANAGER') or hasRole('PAYROLL_MANAGER') or hasRole('ACCOUNTANT')")
    @Operation(summary = "Get employee profile by ID")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> getEmployeeById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        EmployeeResponseDto response = employeeService.getEmployeeById(principal.getTenantId(), id);
        return ResponseEntity.ok(ApiResponse.success(response, "Employee profile retrieved"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER')")
    @Operation(summary = "Update employee profile")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> updateEmployee(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEmployeeRequest request
    ) {
        EmployeeResponseDto response = employeeService.updateEmployee(principal.getTenantId(), principal.getUserId(), id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Employee profile updated successfully"));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER')")
    @Operation(summary = "Update employee employment status")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> updateEmployeeStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam String status
    ) {
        EmployeeResponseDto response = employeeService.updateEmployeeStatus(principal.getTenantId(), principal.getUserId(), id, status);
        return ResponseEntity.ok(ApiResponse.success(response, "Employee status updated"));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER')")
    @Operation(summary = "Activate employee")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> activateEmployee(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        EmployeeResponseDto response = employeeService.updateEmployeeStatus(principal.getTenantId(), principal.getUserId(), id, "ACTIVE");
        return ResponseEntity.ok(ApiResponse.success(response, "Employee activated successfully"));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER')")
    @Operation(summary = "Deactivate employee")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> deactivateEmployee(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        EmployeeResponseDto response = employeeService.updateEmployeeStatus(principal.getTenantId(), principal.getUserId(), id, "INACTIVE");
        return ResponseEntity.ok(ApiResponse.success(response, "Employee deactivated successfully"));
    }

    @PostMapping(value = "/{id}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER')")
    @Operation(summary = "Upload physical document file for employee")
    public ResponseEntity<ApiResponse<EmployeeDocumentDto>> uploadDocument(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "documentType", defaultValue = "OTHER") String documentType
    ) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            originalFilename = "document_" + System.currentTimeMillis();
        }
        // Sanitize file name
        String safeFileName = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");

        // Secure file storage directory under uploads/{tenantId}/{employeeId}/
        String baseUploadDir = System.getProperty("user.dir") + File.separator + "uploads"
                + File.separator + principal.getTenantId() + File.separator + id;
        Path uploadDirPath = Paths.get(baseUploadDir);
        Files.createDirectories(uploadDirPath);

        String storedFileName = UUID.randomUUID() + "_" + safeFileName;
        Path targetPath = uploadDirPath.resolve(storedFileName);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        String contentType = file.getContentType() != null ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        EmployeeDocumentDto docDto = EmployeeDocumentDto.builder()
                .documentType(documentType)
                .fileName(safeFileName)
                .filePath(targetPath.toAbsolutePath().toString())
                .mimeType(contentType)
                .fileSizeBytes(file.getSize())
                .build();

        EmployeeDocumentDto response = employeeService.addEmployeeDocument(
                principal.getTenantId(),
                principal.getUserId(),
                id,
                docDto
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Document uploaded successfully"));
    }

    @GetMapping("/{id}/documents/{documentId}/download")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER') or hasRole('BRANCH_MANAGER')")
    @Operation(summary = "Download employee document securely")
    public ResponseEntity<Resource> downloadDocument(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID documentId
    ) throws IOException {
        EmployeeDocument document = employeeService.getEmployeeDocumentEntity(principal.getTenantId(), id, documentId);

        Path filePath = Paths.get(document.getFilePath());
        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            // Fallback for metadata-only/virtual files: provide bytes with file info
            byte[] content = ("Document: " + document.getFileName() + "\nType: " + document.getDocumentType()).getBytes();
            ByteArrayResource resource = new ByteArrayResource(content);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(document.getMimeType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + document.getFileName() + "\"")
                    .body(resource);
        }

        Resource resource = new FileSystemResource(filePath.toFile());
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(document.getMimeType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + document.getFileName() + "\"")
                .body(resource);
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER')")
    @Operation(summary = "List employee documents")
    public ResponseEntity<ApiResponse<List<EmployeeDocumentDto>>> getEmployeeDocuments(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        List<EmployeeDocumentDto> response = employeeService.getEmployeeDocuments(principal.getTenantId(), id);
        return ResponseEntity.ok(ApiResponse.success(response, "Employee documents retrieved"));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('HR_MANAGER')")
    @Operation(summary = "Get employee audit change history")
    public ResponseEntity<ApiResponse<List<EmployeeAuditLogDto>>> getEmployeeHistory(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        List<EmployeeAuditLogDto> response = employeeService.getEmployeeHistory(principal.getTenantId(), id);
        return ResponseEntity.ok(ApiResponse.success(response, "Employee audit history retrieved"));
    }
}
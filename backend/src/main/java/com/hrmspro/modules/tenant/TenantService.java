package com.hrmspro.modules.tenant;

import com.hrmspro.common.exception.ResourceNotFoundException;
import com.hrmspro.config.security.JwtService;
import com.hrmspro.modules.rbac.Role;
import com.hrmspro.modules.rbac.RoleRepository;
import com.hrmspro.modules.tenant.dto.*;
import com.hrmspro.modules.user.AppUser;
import com.hrmspro.modules.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final BranchRepository branchRepository;
    private final DepartmentRepository departmentRepository;
    private final TenantInvitationRepository invitationRepository;
    private final AppUserRepository userRepository;
    private final RoleRepository roleRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public TenantResponseDto createTenant(UUID creatorUserId, CreateTenantRequest request) {
        String subdomain = request.getSubdomain().trim().toLowerCase();
        String companyCode = request.getCompanyCode().trim().toUpperCase();

        if (tenantRepository.existsBySubdomain(subdomain)) {
            throw new IllegalArgumentException("Subdomain '" + subdomain + "' is already in use");
        }
        if (tenantRepository.existsByCompanyCode(companyCode)) {
            throw new IllegalArgumentException("Company code '" + companyCode + "' is already in use");
        }

        AppUser creator = userRepository.findById(creatorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // 1. Create Tenant
        Tenant tenant = Tenant.builder()
                .name(request.getName().trim())
                .legalName(request.getLegalName() != null ? request.getLegalName().trim() : null)
                .subdomain(subdomain)
                .companyCode(companyCode)
                .email(request.getEmail().trim().toLowerCase())
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .address(request.getAddress())
                .city(request.getCity())
                .state(request.getState())
                .country(request.getCountry() != null ? request.getCountry() : "India")
                .postalCode(request.getPostalCode())
                .industry(request.getIndustry())
                .website(request.getWebsite())
                .timezone(request.getTimezone() != null ? request.getTimezone() : "Asia/Kolkata")
                .subscriptionPlan("FREE_TRIAL")
                .subscriptionStatus("TRIAL")
                .maxEmployees(10)
                .isActive(true)
                .build();

        tenant = tenantRepository.save(tenant);

        // 2. Fetch or create COMPANY_ADMIN system role
        Role companyAdminRole = roleRepository.findByNameAndTenantIdIsNull("COMPANY_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("COMPANY_ADMIN")
                        .description("Company Administrator / Owner")
                        .isSystemRole(true)
                        .build()));

        // 3. Create initial Branch (Head Office)
        Branch defaultBranch = Branch.builder()
                .name("Head Office")
                .code("HO")
                .city(tenant.getCity())
                .state(tenant.getState())
                .country(tenant.getCountry())
                .isActive(true)
                .build();
        defaultBranch.setTenantId(tenant.getId());
        defaultBranch = branchRepository.save(defaultBranch);

        // 4. Create Creator Membership as COMPANY_ADMIN
        TenantMembership membership = TenantMembership.builder()
                .user(creator)
                .branch(defaultBranch)
                .status("ACTIVE")
                .designation("Company Owner")
                .roles(Set.of(companyAdminRole))
                .joinedAt(Instant.now())
                .build();
        membership.setTenantId(tenant.getId());
        membershipRepository.save(membership);

        log.info("Tenant created successfully: {} (ID: {}) by user {}", tenant.getName(), tenant.getId(), creator.getEmail());

        return mapToTenantResponse(tenant, 1L);
    }

    @Transactional(readOnly = true)
    public TenantResponseDto getCurrentTenant(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        long employeeCount = membershipRepository.countByTenantIdAndStatus(tenantId, "ACTIVE");
        return mapToTenantResponse(tenant, employeeCount);
    }

    @Transactional
    public TenantResponseDto updateCurrentTenant(UUID tenantId, UpdateTenantRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        tenant.setName(request.getName().trim());
        tenant.setLegalName(request.getLegalName());
        tenant.setEmail(request.getEmail().trim().toLowerCase());
        tenant.setPhone(request.getPhone());
        tenant.setAddress(request.getAddress());
        tenant.setCity(request.getCity());
        tenant.setState(request.getState());
        if (request.getCountry() != null) tenant.setCountry(request.getCountry());
        tenant.setPostalCode(request.getPostalCode());
        tenant.setIndustry(request.getIndustry());
        tenant.setWebsite(request.getWebsite());
        tenant.setLogoUrl(request.getLogoUrl());
        if (request.getTimezone() != null) tenant.setTimezone(request.getTimezone());

        tenant = tenantRepository.save(tenant);
        long employeeCount = membershipRepository.countByTenantIdAndStatus(tenantId, "ACTIVE");
        return mapToTenantResponse(tenant, employeeCount);
    }

    @Transactional(readOnly = true)
    public List<TenantResponseDto> getMyCompanies(UUID userId) {
        List<TenantMembership> memberships = membershipRepository.findByUserId(userId);
        List<TenantResponseDto> list = new ArrayList<>();
        for (TenantMembership m : memberships) {
            tenantRepository.findById(m.getTenantId()).ifPresent(t -> {
                long count = membershipRepository.countByTenantIdAndStatus(t.getId(), "ACTIVE");
                list.add(mapToTenantResponse(t, count));
            });
        }
        return list;
    }

    // --- Branch Management ---

    @Transactional
    public BranchDto createBranch(UUID tenantId, BranchDto request) {
        String code = request.getCode().trim().toUpperCase();
        if (branchRepository.findByTenantIdAndCode(tenantId, code).isPresent()) {
            throw new IllegalArgumentException("Branch code '" + code + "' already exists in this company");
        }

        Branch branch = Branch.builder()
                .name(request.getName().trim())
                .code(code)
                .address(request.getAddress())
                .city(request.getCity())
                .state(request.getState())
                .country(request.getCountry() != null ? request.getCountry() : "India")
                .isActive(request.getActive() != null ? request.getActive() : true)
                .build();
        branch.setTenantId(tenantId);
        branch = branchRepository.save(branch);

        return mapToBranchDto(branch);
    }

    @Transactional(readOnly = true)
    public List<BranchDto> getBranches(UUID tenantId) {
        return branchRepository.findByTenantId(tenantId).stream()
                .map(this::mapToBranchDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BranchDto getBranchById(UUID tenantId, UUID branchId) {
        Branch branch = branchRepository.findById(branchId)
                .filter(b -> b.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found in this company"));
        return mapToBranchDto(branch);
    }

    @Transactional
    public BranchDto updateBranch(UUID tenantId, UUID branchId, BranchDto request) {
        Branch branch = branchRepository.findById(branchId)
                .filter(b -> b.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found in this company"));

        String newCode = request.getCode().trim().toUpperCase();
        if (!branch.getCode().equalsIgnoreCase(newCode) &&
                branchRepository.findByTenantIdAndCode(tenantId, newCode).isPresent()) {
            throw new IllegalArgumentException("Branch code '" + newCode + "' is already in use");
        }

        branch.setName(request.getName().trim());
        branch.setCode(newCode);
        branch.setAddress(request.getAddress());
        branch.setCity(request.getCity());
        branch.setState(request.getState());
        if (request.getCountry() != null) branch.setCountry(request.getCountry());
        if (request.getActive() != null) branch.setIsActive(request.getActive());

        branch = branchRepository.save(branch);
        return mapToBranchDto(branch);
    }

    @Transactional
    public BranchDto updateBranchStatus(UUID tenantId, UUID branchId, boolean active) {
        Branch branch = branchRepository.findById(branchId)
                .filter(b -> b.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found in this company"));
        branch.setIsActive(active);
        branch = branchRepository.save(branch);
        return mapToBranchDto(branch);
    }

    // --- Department Management ---

    @Transactional
    public DepartmentDto createDepartment(UUID tenantId, DepartmentDto request) {
        Branch branch = null;
        if (request.getBranchId() != null) {
            branch = branchRepository.findById(request.getBranchId())
                    .filter(b -> b.getTenantId().equals(tenantId))
                    .orElseThrow(() -> new IllegalArgumentException("Branch does not belong to this company"));
        }

        Department department = Department.builder()
                .branch(branch)
                .name(request.getName().trim())
                .code(request.getCode().trim().toUpperCase())
                .isActive(request.getActive() != null ? request.getActive() : true)
                .build();
        department.setTenantId(tenantId);
        department = departmentRepository.save(department);

        return mapToDepartmentDto(department);
    }

    @Transactional(readOnly = true)
    public List<DepartmentDto> getDepartments(UUID tenantId) {
        return departmentRepository.findByTenantId(tenantId).stream()
                .map(this::mapToDepartmentDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DepartmentDto getDepartmentById(UUID tenantId, UUID deptId) {
        Department dept = departmentRepository.findById(deptId)
                .filter(d -> d.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Department not found in this company"));
        return mapToDepartmentDto(dept);
    }

    @Transactional
    public DepartmentDto updateDepartment(UUID tenantId, UUID deptId, DepartmentDto request) {
        Department dept = departmentRepository.findById(deptId)
                .filter(d -> d.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Department not found in this company"));

        if (request.getBranchId() != null) {
            Branch branch = branchRepository.findById(request.getBranchId())
                    .filter(b -> b.getTenantId().equals(tenantId))
                    .orElseThrow(() -> new IllegalArgumentException("Branch does not belong to this company"));
            dept.setBranch(branch);
        } else {
            dept.setBranch(null);
        }

        dept.setName(request.getName().trim());
        dept.setCode(request.getCode().trim().toUpperCase());
        if (request.getActive() != null) dept.setIsActive(request.getActive());

        dept = departmentRepository.save(dept);
        return mapToDepartmentDto(dept);
    }

    @Transactional
    public DepartmentDto updateDepartmentStatus(UUID tenantId, UUID deptId, boolean active) {
        Department dept = departmentRepository.findById(deptId)
                .filter(d -> d.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Department not found in this company"));
        dept.setIsActive(active);
        dept = departmentRepository.save(dept);
        return mapToDepartmentDto(dept);
    }

    // --- Membership & Invitation Foundation ---

    @Transactional
    public InvitationResponseDto inviteMember(UUID tenantId, UUID inviterUserId, InviteMemberRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        // Capacity enforcement
        long activeCount = membershipRepository.countByTenantIdAndStatus(tenantId, "ACTIVE");
        if (activeCount >= tenant.getMaxEmployees()) {
            throw new IllegalStateException("Company employee capacity limit reached (" +
                    activeCount + "/" + tenant.getMaxEmployees() + "). Please upgrade subscription plan.");
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();

        // Check if user is already a member
        Optional<AppUser> existingUser = userRepository.findByEmail(normalizedEmail);
        if (existingUser.isPresent() && membershipRepository.existsByUserIdAndTenantId(existingUser.get().getId(), tenantId)) {
            throw new IllegalArgumentException("User with email " + normalizedEmail + " is already a member of this company");
        }

        if (invitationRepository.existsByTenantIdAndEmailAndStatus(tenantId, normalizedEmail, "PENDING")) {
            throw new IllegalArgumentException("A pending invitation already exists for " + normalizedEmail);
        }

        AppUser inviter = userRepository.findById(inviterUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Inviter not found"));

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));

        // Guard: Prevent granting SUPER_ADMIN
        if ("SUPER_ADMIN".equalsIgnoreCase(role.getName())) {
            throw new AccessDeniedException("Cannot invite users with SUPER_ADMIN role");
        }

        Branch branch = null;
        if (request.getBranchId() != null) {
            branch = branchRepository.findById(request.getBranchId())
                    .filter(b -> b.getTenantId().equals(tenantId))
                    .orElseThrow(() -> new IllegalArgumentException("Branch does not belong to this company"));
        }

        Department department = null;
        if (request.getDepartmentId() != null) {
            department = departmentRepository.findById(request.getDepartmentId())
                    .filter(d -> d.getTenantId().equals(tenantId))
                    .orElseThrow(() -> new IllegalArgumentException("Department does not belong to this company"));
        }

        String rawToken = generateSecureToken();
        String tokenHash = JwtService.hashToken(rawToken);

        TenantInvitation invitation = TenantInvitation.builder()
                .email(normalizedEmail)
                .role(role)
                .branch(branch)
                .department(department)
                .tokenHash(tokenHash)
                .invitedBy(inviter)
                .designation(request.getDesignation())
                .employeeCode(request.getEmployeeCode())
                .status("PENDING")
                .expiresAt(Instant.now().plusSeconds(604800)) // 7 days
                .build();
        invitation.setTenantId(tenantId);
        invitation = invitationRepository.save(invitation);

        InvitationResponseDto dto = mapToInvitationDto(invitation);
        dto.setRawToken(rawToken); // returned only in creation response
        return dto;
    }

    @Transactional
    public void acceptInvitation(UUID currentUserId, AcceptInvitationRequest request) {
        String tokenHash = JwtService.hashToken(request.getToken());

        TenantInvitation invitation = invitationRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invitation token"));

        if (!"PENDING".equalsIgnoreCase(invitation.getStatus())) {
            throw new IllegalStateException("Invitation is no longer valid (Status: " + invitation.getStatus() + ")");
        }

        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            invitation.setStatus("EXPIRED");
            invitationRepository.save(invitation);
            throw new IllegalStateException("Invitation has expired");
        }

        AppUser user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.getEmail().equalsIgnoreCase(invitation.getEmail())) {
            throw new AccessDeniedException("Invitation was issued to " + invitation.getEmail() +
                    ", but current authenticated account is " + user.getEmail());
        }

        Tenant tenant = tenantRepository.findById(invitation.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));

        // Re-check capacity limit
        long activeCount = membershipRepository.countByTenantIdAndStatus(tenant.getId(), "ACTIVE");
        if (activeCount >= tenant.getMaxEmployees()) {
            throw new IllegalStateException("Company has reached maximum capacity of " + tenant.getMaxEmployees() + " employees.");
        }

        // Create or activate membership
        TenantMembership membership = membershipRepository.findByUserIdAndTenantId(user.getId(), tenant.getId())
                .orElseGet(() -> {
                    TenantMembership m = TenantMembership.builder()
                            .user(user)
                            .branch(invitation.getBranch())
                            .department(invitation.getDepartment())
                            .designation(invitation.getDesignation())
                            .employeeCode(invitation.getEmployeeCode())
                            .status("ACTIVE")
                            .joinedAt(Instant.now())
                            .roles(new HashSet<>(Set.of(invitation.getRole())))
                            .build();
                    m.setTenantId(tenant.getId());
                    return m;
                });

        membership.setStatus("ACTIVE");
        membership.setBranch(invitation.getBranch());
        membership.setDepartment(invitation.getDepartment());
        membership.setDesignation(invitation.getDesignation());
        membership.setEmployeeCode(invitation.getEmployeeCode());
        membership.getRoles().add(invitation.getRole());
        membershipRepository.save(membership);

        invitation.setStatus("ACCEPTED");
        invitation.setAcceptedAt(Instant.now());
        invitationRepository.save(invitation);

        log.info("Invitation accepted: User {} joined company {}", user.getEmail(), tenant.getName());
    }

    @Transactional
    public void updateMembershipStatus(UUID tenantId, UUID membershipId, String status) {
        TenantMembership membership = membershipRepository.findById(membershipId)
                .filter(m -> m.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found in this company"));

        if (!List.of("ACTIVE", "SUSPENDED", "REVOKED").contains(status.toUpperCase())) {
            throw new IllegalArgumentException("Invalid status: " + status);
        }

        membership.setStatus(status.toUpperCase());
        membershipRepository.save(membership);
    }

    // --- Helpers ---

    private TenantResponseDto mapToTenantResponse(Tenant tenant, long employeeCount) {
        return TenantResponseDto.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .legalName(tenant.getLegalName())
                .subdomain(tenant.getSubdomain())
                .companyCode(tenant.getCompanyCode())
                .email(tenant.getEmail())
                .phone(tenant.getPhone())
                .address(tenant.getAddress())
                .city(tenant.getCity())
                .state(tenant.getState())
                .country(tenant.getCountry())
                .postalCode(tenant.getPostalCode())
                .industry(tenant.getIndustry())
                .website(tenant.getWebsite())
                .logoUrl(tenant.getLogoUrl())
                .timezone(tenant.getTimezone())
                .subscriptionPlan(tenant.getSubscriptionPlan())
                .subscriptionStatus(tenant.getSubscriptionStatus())
                .maxEmployees(tenant.getMaxEmployees())
                .currentEmployeeCount(employeeCount)
                .active(tenant.getIsActive())
                .createdAt(tenant.getCreatedAt())
                .build();
    }

    private BranchDto mapToBranchDto(Branch branch) {
        return BranchDto.builder()
                .id(branch.getId())
                .name(branch.getName())
                .code(branch.getCode())
                .address(branch.getAddress())
                .city(branch.getCity())
                .state(branch.getState())
                .country(branch.getCountry())
                .active(branch.getIsActive())
                .build();
    }

    private DepartmentDto mapToDepartmentDto(Department dept) {
        return DepartmentDto.builder()
                .id(dept.getId())
                .branchId(dept.getBranch() != null ? dept.getBranch().getId() : null)
                .branchName(dept.getBranch() != null ? dept.getBranch().getName() : null)
                .name(dept.getName())
                .code(dept.getCode())
                .active(dept.getIsActive())
                .build();
    }

    private InvitationResponseDto mapToInvitationDto(TenantInvitation inv) {
        return InvitationResponseDto.builder()
                .id(inv.getId())
                .tenantId(inv.getTenantId())
                .email(inv.getEmail())
                .roleName(inv.getRole() != null ? inv.getRole().getName() : null)
                .branchName(inv.getBranch() != null ? inv.getBranch().getName() : null)
                .departmentName(inv.getDepartment() != null ? inv.getDepartment().getName() : null)
                .designation(inv.getDesignation())
                .employeeCode(inv.getEmployeeCode())
                .status(inv.getStatus())
                .expiresAt(inv.getExpiresAt())
                .build();
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
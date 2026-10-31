package com.hrmspro.modules.leave;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrmspro.common.exception.ResourceNotFoundException;
import com.hrmspro.modules.employee.Employee;
import com.hrmspro.modules.employee.EmployeeRepository;
import com.hrmspro.modules.leave.dto.*;
import com.hrmspro.modules.shift.ShiftAssignmentService;
import com.hrmspro.modules.shift.ShiftRoster;
import com.hrmspro.modules.shift.ShiftRosterRepository;
import com.hrmspro.modules.shift.ShiftService;
import com.hrmspro.modules.user.AppUser;
import com.hrmspro.modules.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveTypeRepository leaveTypeRepository;
    private final LeavePolicyRepository leavePolicyRepository;
    private final EmployeeLeaveBalanceRepository balanceRepository;
    private final LeaveLedgerRepository ledgerRepository;
    private final LeaveApplicationRepository applicationRepository;
    private final LeaveApprovalStepRepository approvalStepRepository;
    private final HolidayRepository holidayRepository;
    private final EmployeeRepository employeeRepository;
    private final AppUserRepository userRepository;
    private final ShiftAssignmentService shiftAssignmentService;
    private final ShiftService shiftService;
    private final ShiftRosterRepository rosterRepository;
    private final ObjectMapper objectMapper;

    // =====================================================================
    // 1. Leave Type CRUD
    // =====================================================================

    @Transactional
    public LeaveTypeDto createLeaveType(UUID tenantId, LeaveTypeDto request) {
        String code = request.getCode().trim().toUpperCase();
        if (leaveTypeRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new IllegalArgumentException("Leave type code '" + code + "' already exists in this company");
        }

        LeaveType leaveType = LeaveType.builder()
                .name(request.getName().trim())
                .code(code)
                .description(request.getDescription())
                .isPaid(request.getIsPaid() == null || request.getIsPaid())
                .category(request.getCategory() != null ? request.getCategory() : "ANNUAL")
                .isHalfDayAllowed(request.getIsHalfDayAllowed() == null || request.getIsHalfDayAllowed())
                .requiresAttachment(Boolean.TRUE.equals(request.getRequiresAttachment()))
                .attachmentMandatoryDays(request.getAttachmentMandatoryDays() != null ? request.getAttachmentMandatoryDays() : 3)
                .isActive(request.getIsActive() == null || request.getIsActive())
                .build();
        leaveType.setTenantId(tenantId);
        leaveType = leaveTypeRepository.save(leaveType);
        return mapToTypeDto(leaveType);
    }

    @Transactional(readOnly = true)
    public Page<LeaveTypeDto> getLeaveTypes(UUID tenantId, Pageable pageable) {
        return leaveTypeRepository.findByTenantId(tenantId, pageable).map(this::mapToTypeDto);
    }

    @Transactional(readOnly = true)
    public List<LeaveTypeDto> getActiveLeaveTypes(UUID tenantId) {
        return leaveTypeRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
                .map(this::mapToTypeDto).collect(Collectors.toList());
    }

    // =====================================================================
    // 2. Leave Policy Configuration
    // =====================================================================

    @Transactional
    public LeavePolicyDto configurePolicy(UUID tenantId, LeavePolicyDto request) {
        LeaveType leaveType = leaveTypeRepository.findByIdAndTenantId(request.getLeaveTypeId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found"));

        int year = request.getPolicyEffectiveYear() != null ? request.getPolicyEffectiveYear() : LocalDate.now().getYear();

        LeavePolicy policy = leavePolicyRepository.findByTenantIdAndLeaveTypeIdAndPolicyEffectiveYear(tenantId, leaveType.getId(), year)
                .orElse(LeavePolicy.builder().leaveType(leaveType).policyEffectiveYear(year).build());

        policy.setName(request.getName() != null ? request.getName().trim() : leaveType.getName() + " Policy " + year);
        policy.setAllocationDays(request.getAllocationDays() != null ? request.getAllocationDays() : BigDecimal.ZERO);
        policy.setAccrualFrequency(request.getAccrualFrequency() != null ? request.getAccrualFrequency() : "MONTHLY");
        policy.setAccrualRate(request.getAccrualRate() != null ? request.getAccrualRate() : BigDecimal.ONE);
        policy.setMaxConsecutiveDays(request.getMaxConsecutiveDays());
        policy.setMinNoticeDays(request.getMinNoticeDays() != null ? request.getMinNoticeDays() : 0);
        policy.setAllowCarryForward(Boolean.TRUE.equals(request.getAllowCarryForward()));
        policy.setMaxCarryForwardDays(request.getMaxCarryForwardDays() != null ? request.getMaxCarryForwardDays() : BigDecimal.ZERO);
        policy.setCarryForwardExpiryMonths(request.getCarryForwardExpiryMonths());
        policy.setAllowNegativeBalance(Boolean.TRUE.equals(request.getAllowNegativeBalance()));
        policy.setMaxNegativeBalance(request.getMaxNegativeBalance() != null ? request.getMaxNegativeBalance() : BigDecimal.ZERO);
        policy.setCountWeeklyOffAsLeave(Boolean.TRUE.equals(request.getCountWeeklyOffAsLeave()));
        policy.setCountHolidayAsLeave(Boolean.TRUE.equals(request.getCountHolidayAsLeave()));
        policy.setIsActive(request.getIsActive() == null || request.getIsActive());
        policy.setTenantId(tenantId);

        policy = leavePolicyRepository.save(policy);
        return mapToPolicyDto(policy);
    }

    // =====================================================================
    // 3. Leave Balance & Accrual Engine (Idempotent Ledger)
    // =====================================================================

    @Transactional
    public EmployeeLeaveBalance getOrCreateBalance(UUID tenantId, UUID employeeId, UUID leaveTypeId, int year) {
        return balanceRepository.findByTenantIdAndEmployeeIdAndLeaveTypeIdAndCycleYear(tenantId, employeeId, leaveTypeId, year)
                .orElseGet(() -> {
                    Employee employee = employeeRepository.findByIdAndTenantId(employeeId, tenantId)
                            .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
                    LeaveType leaveType = leaveTypeRepository.findByIdAndTenantId(leaveTypeId, tenantId)
                            .orElseThrow(() -> new ResourceNotFoundException("Leave type not found"));

                    EmployeeLeaveBalance bal = EmployeeLeaveBalance.builder()
                            .employee(employee)
                            .leaveType(leaveType)
                            .cycleYear(year)
                            .openingBalance(BigDecimal.ZERO)
                            .accruedAmount(BigDecimal.ZERO)
                            .creditedAmount(BigDecimal.ZERO)
                            .usedAmount(BigDecimal.ZERO)
                            .pendingApprovalAmount(BigDecimal.ZERO)
                            .expiredAmount(BigDecimal.ZERO)
                            .carriedForwardAmount(BigDecimal.ZERO)
                            .build();
                    bal.setTenantId(tenantId);
                    return balanceRepository.save(bal);
                });
    }

    @Transactional
    public void executePeriodicAccrual(UUID tenantId, int year, int month, UUID actorUserId) {
        AppUser actor = userRepository.findById(actorUserId).orElse(null);
        List<LeavePolicy> policies = leavePolicyRepository.findByTenantIdAndPolicyEffectiveYearAndIsActiveTrue(tenantId, year);
        List<Employee> employees = employeeRepository.findByTenantId(tenantId, Pageable.unpaged()).getContent();

        for (LeavePolicy policy : policies) {
            if ("MONTHLY".equalsIgnoreCase(policy.getAccrualFrequency())) {
                BigDecimal rate = policy.getAccrualRate();
                for (Employee emp : employees) {
                    if (!"ACTIVE".equals(emp.getEmploymentStatus()) && !"PROBATION".equals(emp.getEmploymentStatus())) {
                        continue;
                    }

                    String idempotencyKey = String.format("ACCRUAL_%d_%02d_%s_%s", year, month, policy.getLeaveType().getId(), emp.getId());
                    if (ledgerRepository.existsByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)) {
                        continue; // Idempotent: already accrued
                    }

                    EmployeeLeaveBalance balance = getOrCreateBalance(tenantId, emp.getId(), policy.getLeaveType().getId(), year);
                    balance.setAccruedAmount(balance.getAccruedAmount().add(rate));
                    balance = balanceRepository.save(balance);

                    LeaveLedger ledger = LeaveLedger.builder()
                            .employee(emp)
                            .leaveType(policy.getLeaveType())
                            .cycleYear(year)
                            .transactionType("ACCRUAL")
                            .amount(rate)
                            .balanceAfter(balance.getAvailableBalance())
                            .idempotencyKey(idempotencyKey)
                            .notes("Monthly accrual for " + year + "-" + month)
                            .createdBy(actor)
                            .build();
                    ledger.setTenantId(tenantId);
                    ledgerRepository.save(ledger);
                }
            }
        }
    }

    @Transactional
    public LeaveBalanceDto manualAdjustment(UUID tenantId, UUID actorUserId, ManualAdjustmentRequest request) {
        AppUser actor = userRepository.findById(actorUserId).orElse(null);
        int year = request.getCycleYear() != null ? request.getCycleYear() : LocalDate.now().getYear();

        EmployeeLeaveBalance balance = getOrCreateBalance(tenantId, request.getEmployeeId(), request.getLeaveTypeId(), year);
        BigDecimal amount = request.getAmount();

        if (amount.compareTo(BigDecimal.ZERO) >= 0) {
            balance.setCreditedAmount(balance.getCreditedAmount().add(amount));
        } else {
            balance.setUsedAmount(balance.getUsedAmount().add(amount.abs()));
        }
        balance = balanceRepository.save(balance);

        String idempotencyKey = "ADJUST_" + UUID.randomUUID();
        LeaveLedger ledger = LeaveLedger.builder()
                .employee(balance.getEmployee())
                .leaveType(balance.getLeaveType())
                .cycleYear(year)
                .transactionType("MANUAL_ADJUSTMENT")
                .amount(amount)
                .balanceAfter(balance.getAvailableBalance())
                .idempotencyKey(idempotencyKey)
                .notes(request.getReason())
                .createdBy(actor)
                .build();
        ledger.setTenantId(tenantId);
        ledgerRepository.save(ledger);

        return mapToBalanceDto(balance);
    }

    // =====================================================================
    // 4. Chargeable Leave Days Calculation Engine
    // =====================================================================

    public BigDecimal calculateChargeableDays(UUID tenantId, Employee employee, LeavePolicy policy,
                                            LocalDate startDate, LocalDate endDate, boolean isHalfDay) {
        if (isHalfDay) {
            return new BigDecimal("0.50");
        }

        UUID branchId = employee.getBranch() != null ? employee.getBranch().getId() : null;
        List<Holiday> holidays = holidayRepository.findHolidaysForBranchAndDateRange(tenantId, branchId, startDate, endDate);
        Set<LocalDate> holidayDates = holidays.stream().map(Holiday::getHolidayDate).collect(Collectors.toSet());

        BigDecimal total = BigDecimal.ZERO;
        LocalDate cursor = startDate;

        while (!cursor.isAfter(endDate)) {
            final LocalDate currentDate = cursor;
            boolean isHoliday = holidayDates.contains(currentDate);

            // Check published roster first
            Optional<ShiftRoster> rosterOpt = rosterRepository.findByTenantIdAndEmployeeIdAndRosterDate(tenantId, employee.getId(), currentDate);
            boolean isWeeklyOff = false;

            if (rosterOpt.isPresent() && "PUBLISHED".equals(rosterOpt.get().getStatus())) {
                isWeeklyOff = Boolean.TRUE.equals(rosterOpt.get().getIsWeeklyOff());
            } else {
                // Fallback to active shift assignment
                var assignmentOpt = shiftAssignmentService.resolveEffectiveAssignment(employee.getId(), currentDate);
                if (assignmentOpt.isPresent()) {
                    isWeeklyOff = shiftService.isWeeklyOff(assignmentOpt.get().getShiftTemplate(), currentDate);
                }
            }

            boolean countDay = true;
            if (isWeeklyOff && !Boolean.TRUE.equals(policy.getCountWeeklyOffAsLeave())) {
                countDay = false;
            }
            if (isHoliday && !Boolean.TRUE.equals(policy.getCountHolidayAsLeave())) {
                countDay = false;
            }

            if (countDay) {
                total = total.add(BigDecimal.ONE);
            }

            cursor = cursor.plusDays(1);
        }

        return total.setScale(2, RoundingMode.HALF_UP);
    }

    // =====================================================================
    // 5. Leave Application & Cancellation
    // =====================================================================

    @Transactional
    public LeaveApplicationDto applyLeave(UUID tenantId, UUID userId, ApplyLeaveRequest request) {
        Employee employee = employeeRepository.findByUserIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee profile not found for authenticated user"));

        LeaveType leaveType = leaveTypeRepository.findByIdAndTenantId(request.getLeaveTypeId(), tenantId)
                .filter(LeaveType::getIsActive)
                .orElseThrow(() -> new IllegalArgumentException("Leave type not found or inactive"));

        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new IllegalArgumentException("Start date and end date are required");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }

        // Prevent overlapping leave applications
        if (applicationRepository.hasOverlappingLeave(employee.getId(), request.getStartDate(), request.getEndDate())) {
            throw new IllegalStateException("An overlapping leave application is already pending or approved for this period");
        }

        int year = request.getStartDate().getYear();
        LeavePolicy policy = leavePolicyRepository.findByTenantIdAndLeaveTypeIdAndPolicyEffectiveYear(tenantId, leaveType.getId(), year)
                .orElseGet(() -> LeavePolicy.builder()
                        .leaveType(leaveType)
                        .policyEffectiveYear(year)
                        .allocationDays(BigDecimal.ZERO)
                        .build());

        BigDecimal chargeableDays = calculateChargeableDays(tenantId, employee, policy,
                request.getStartDate(), request.getEndDate(), request.isHalfDay());

        if (chargeableDays.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Requested dates fall entirely on non-working weekly offs or holidays");
        }

        // Validate max consecutive days
        if (policy.getMaxConsecutiveDays() != null && chargeableDays.compareTo(BigDecimal.valueOf(policy.getMaxConsecutiveDays())) > 0) {
            throw new IllegalArgumentException("Requested leave exceeds maximum consecutive limit of " + policy.getMaxConsecutiveDays() + " days");
        }

        // Check available balance
        EmployeeLeaveBalance balance = getOrCreateBalance(tenantId, employee.getId(), leaveType.getId(), year);
        BigDecimal available = balance.getAvailableBalance();

        if (available.compareTo(chargeableDays) < 0 && !Boolean.TRUE.equals(policy.getAllowNegativeBalance())) {
            throw new IllegalStateException("Insufficient leave balance. Available: " + available + ", Requested: " + chargeableDays);
        }

        // Hold balance in pending
        balance.setPendingApprovalAmount(balance.getPendingApprovalAmount().add(chargeableDays));
        balanceRepository.save(balance);

        String policySnapshot = serializePolicySnapshot(policy);

        LeaveApplication app = LeaveApplication.builder()
                .employee(employee)
                .leaveType(leaveType)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .isHalfDay(request.isHalfDay())
                .halfDaySession(request.getHalfDaySession())
                .totalDays(chargeableDays)
                .reason(request.getReason())
                .attachmentUrl(request.getAttachmentUrl())
                .status("PENDING")
                .policySnapshot(policySnapshot)
                .build();
        app.setTenantId(tenantId);
        app = applicationRepository.save(app);

        // Create initial approval step for reporting manager (or HR fallback)
        AppUser approver = (employee.getReportingManager() != null && employee.getReportingManager().getUser() != null)
                ? employee.getReportingManager().getUser() : null;

        LeaveApprovalStep step = LeaveApprovalStep.builder()
                .leaveApplication(app)
                .stepNumber(1)
                .approverUser(approver)
                .status("PENDING")
                .build();
        step.setTenantId(tenantId);
        approvalStepRepository.save(step);

        return mapToAppDto(app);
    }

    @Transactional
    public LeaveApplicationDto cancelLeave(UUID tenantId, UUID userId, UUID applicationId) {
        Employee employee = employeeRepository.findByUserIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee profile not found"));

        LeaveApplication app = applicationRepository.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave application not found"));

        if (!app.getEmployee().getId().equals(employee.getId())) {
            throw new AccessDeniedException("Cannot cancel another employee's leave application");
        }

        int year = app.getStartDate().getYear();
        EmployeeLeaveBalance balance = getOrCreateBalance(tenantId, employee.getId(), app.getLeaveType().getId(), year);

        if ("PENDING".equals(app.getStatus())) {
            balance.setPendingApprovalAmount(balance.getPendingApprovalAmount().subtract(app.getTotalDays()));
            balanceRepository.save(balance);
            app.setStatus("CANCELLED");
        } else if ("APPROVED".equals(app.getStatus())) {
            // Reversal of used days
            balance.setUsedAmount(balance.getUsedAmount().subtract(app.getTotalDays()));
            balanceRepository.save(balance);

            LeaveLedger ledger = LeaveLedger.builder()
                    .employee(employee)
                    .leaveType(app.getLeaveType())
                    .leaveApplication(app)
                    .cycleYear(year)
                    .transactionType("CREDIT_REVERSAL")
                    .amount(app.getTotalDays())
                    .balanceAfter(balance.getAvailableBalance())
                    .notes("Reversal of approved leave #" + applicationId)
                    .build();
            ledger.setTenantId(tenantId);
            ledgerRepository.save(ledger);

            app.setStatus("CANCELLED");
        } else {
            throw new IllegalStateException("Cannot cancel application with status: " + app.getStatus());
        }

        app = applicationRepository.save(app);
        return mapToAppDto(app);
    }

    // =====================================================================
    // 6. Approval & Rejection Workflow
    // =====================================================================

    @Transactional
    public LeaveApplicationDto approveLeave(UUID tenantId, UUID reviewerUserId, UUID applicationId, String comments) {
        LeaveApplication app = applicationRepository.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave application not found"));

        if (!"PENDING".equals(app.getStatus())) {
            throw new IllegalStateException("Only PENDING leave applications can be approved");
        }

        // Self-approval prevention
        if (app.getEmployee().getUser() != null && app.getEmployee().getUser().getId().equals(reviewerUserId)) {
            throw new AccessDeniedException("Requesters cannot approve their own leave applications");
        }

        AppUser reviewer = userRepository.findById(reviewerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Reviewer user not found"));

        int year = app.getStartDate().getYear();
        EmployeeLeaveBalance balance = getOrCreateBalance(tenantId, app.getEmployee().getId(), app.getLeaveType().getId(), year);

        // Deduct from pending and add to used
        balance.setPendingApprovalAmount(balance.getPendingApprovalAmount().subtract(app.getTotalDays()));
        balance.setUsedAmount(balance.getUsedAmount().add(app.getTotalDays()));
        balance = balanceRepository.save(balance);

        // Record in ledger
        LeaveLedger ledger = LeaveLedger.builder()
                .employee(app.getEmployee())
                .leaveType(app.getLeaveType())
                .leaveApplication(app)
                .cycleYear(year)
                .transactionType("DEBIT")
                .amount(app.getTotalDays().negate())
                .balanceAfter(balance.getAvailableBalance())
                .notes("Approved leave application #" + applicationId)
                .createdBy(reviewer)
                .build();
        ledger.setTenantId(tenantId);
        ledgerRepository.save(ledger);

        // Update approval step
        List<LeaveApprovalStep> steps = approvalStepRepository.findByLeaveApplicationIdOrderByStepNumberAsc(app.getId());
        if (!steps.isEmpty()) {
            LeaveApprovalStep currentStep = steps.get(0);
            currentStep.setStatus("APPROVED");
            currentStep.setApproverUser(reviewer);
            currentStep.setComments(comments);
            currentStep.setActionTime(java.time.Instant.now());
            approvalStepRepository.save(currentStep);
        }

        app.setStatus("APPROVED");
        app = applicationRepository.save(app);
        return mapToAppDto(app);
    }

    @Transactional
    public LeaveApplicationDto rejectLeave(UUID tenantId, UUID reviewerUserId, UUID applicationId, String comments) {
        LeaveApplication app = applicationRepository.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave application not found"));

        if (!"PENDING".equals(app.getStatus())) {
            throw new IllegalStateException("Only PENDING leave applications can be rejected");
        }

        if (app.getEmployee().getUser() != null && app.getEmployee().getUser().getId().equals(reviewerUserId)) {
            throw new AccessDeniedException("Requesters cannot reject their own leave applications");
        }

        AppUser reviewer = userRepository.findById(reviewerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Reviewer user not found"));

        int year = app.getStartDate().getYear();
        EmployeeLeaveBalance balance = getOrCreateBalance(tenantId, app.getEmployee().getId(), app.getLeaveType().getId(), year);

        // Release pending approval amount
        balance.setPendingApprovalAmount(balance.getPendingApprovalAmount().subtract(app.getTotalDays()));
        balanceRepository.save(balance);

        List<LeaveApprovalStep> steps = approvalStepRepository.findByLeaveApplicationIdOrderByStepNumberAsc(app.getId());
        if (!steps.isEmpty()) {
            LeaveApprovalStep currentStep = steps.get(0);
            currentStep.setStatus("REJECTED");
            currentStep.setApproverUser(reviewer);
            currentStep.setComments(comments);
            currentStep.setActionTime(java.time.Instant.now());
            approvalStepRepository.save(currentStep);
        }

        app.setStatus("REJECTED");
        app = applicationRepository.save(app);
        return mapToAppDto(app);
    }

    // =====================================================================
    // 7. Queries & Reporting
    // =====================================================================

    @Transactional(readOnly = true)
    public List<LeaveApplicationDto> getMyRequests(UUID tenantId, UUID userId) {
        Employee employee = employeeRepository.findByUserIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee profile not found"));
        return applicationRepository.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(tenantId, employee.getId())
                .stream().map(this::mapToAppDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceDto> getMyBalances(UUID tenantId, UUID userId) {
        Employee employee = employeeRepository.findByUserIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee profile not found"));
        int year = LocalDate.now().getYear();
        return balanceRepository.findByTenantIdAndEmployeeIdAndCycleYear(tenantId, employee.getId(), year)
                .stream().map(this::mapToBalanceDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public LeaveApplicationDto getApplicationById(UUID tenantId, UUID id) {
        LeaveApplication app = applicationRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave application not found"));
        return mapToAppDto(app);
    }

    @Transactional(readOnly = true)
    public Page<LeaveApplicationDto> getPendingApprovals(UUID tenantId, Pageable pageable) {
        return applicationRepository.findByTenantIdAndStatus(tenantId, "PENDING", pageable).map(this::mapToAppDto);
    }

    @Transactional(readOnly = true)
    public Page<LeaveApplicationDto> getLeaveReport(UUID tenantId, UUID employeeId, UUID branchId, UUID departmentId,
                                                    UUID leaveTypeId, String status, LocalDate start, LocalDate end, Pageable pageable) {
        return applicationRepository.findLeaveReports(tenantId, employeeId, branchId, departmentId, leaveTypeId, status, start, end, pageable)
                .map(this::mapToAppDto);
    }

    @Transactional(readOnly = true)
    public boolean isEmployeeOnApprovedLeave(UUID tenantId, UUID employeeId, LocalDate date) {
        List<LeaveApplication> list = applicationRepository.findApprovedLeaveOnDate(tenantId, employeeId, date);
        return !list.isEmpty();
    }

    // =====================================================================
    // Helpers & Mappers
    // =====================================================================

    private String serializePolicySnapshot(LeavePolicy policy) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("policyId", policy.getId());
            map.put("leaveTypeCode", policy.getLeaveType().getCode());
            map.put("allocationDays", policy.getAllocationDays());
            map.put("countWeeklyOffAsLeave", policy.getCountWeeklyOffAsLeave());
            map.put("countHolidayAsLeave", policy.getCountHolidayAsLeave());
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    public LeaveTypeDto mapToTypeDto(LeaveType t) {
        return LeaveTypeDto.builder()
                .id(t.getId())
                .name(t.getName())
                .code(t.getCode())
                .description(t.getDescription())
                .isPaid(t.getIsPaid())
                .category(t.getCategory())
                .isHalfDayAllowed(t.getIsHalfDayAllowed())
                .requiresAttachment(t.getRequiresAttachment())
                .attachmentMandatoryDays(t.getAttachmentMandatoryDays())
                .isActive(t.getIsActive())
                .build();
    }

    public LeavePolicyDto mapToPolicyDto(LeavePolicy p) {
        return LeavePolicyDto.builder()
                .id(p.getId())
                .leaveTypeId(p.getLeaveType().getId())
                .leaveTypeCode(p.getLeaveType().getCode())
                .leaveTypeName(p.getLeaveType().getName())
                .name(p.getName())
                .allocationDays(p.getAllocationDays())
                .accrualFrequency(p.getAccrualFrequency())
                .accrualRate(p.getAccrualRate())
                .maxConsecutiveDays(p.getMaxConsecutiveDays())
                .minNoticeDays(p.getMinNoticeDays())
                .allowCarryForward(p.getAllowCarryForward())
                .maxCarryForwardDays(p.getMaxCarryForwardDays())
                .carryForwardExpiryMonths(p.getCarryForwardExpiryMonths())
                .allowNegativeBalance(p.getAllowNegativeBalance())
                .maxNegativeBalance(p.getMaxNegativeBalance())
                .countWeeklyOffAsLeave(p.getCountWeeklyOffAsLeave())
                .countHolidayAsLeave(p.getCountHolidayAsLeave())
                .policyEffectiveYear(p.getPolicyEffectiveYear())
                .isActive(p.getIsActive())
                .build();
    }

    public LeaveBalanceDto mapToBalanceDto(EmployeeLeaveBalance b) {
        return LeaveBalanceDto.builder()
                .id(b.getId())
                .employeeId(b.getEmployee().getId())
                .employeeName(b.getEmployee().getFirstName() + " " + b.getEmployee().getLastName())
                .employeeCode(b.getEmployee().getEmployeeCode())
                .leaveTypeId(b.getLeaveType().getId())
                .leaveTypeCode(b.getLeaveType().getCode())
                .leaveTypeName(b.getLeaveType().getName())
                .cycleYear(b.getCycleYear())
                .openingBalance(b.getOpeningBalance())
                .accruedAmount(b.getAccruedAmount())
                .creditedAmount(b.getCreditedAmount())
                .usedAmount(b.getUsedAmount())
                .pendingApprovalAmount(b.getPendingApprovalAmount())
                .expiredAmount(b.getExpiredAmount())
                .carriedForwardAmount(b.getCarriedForwardAmount())
                .availableBalance(b.getAvailableBalance())
                .build();
    }

    public LeaveApplicationDto mapToAppDto(LeaveApplication a) {
        List<LeaveApprovalStepDto> steps = approvalStepRepository.findByLeaveApplicationIdOrderByStepNumberAsc(a.getId())
                .stream().map(s -> LeaveApprovalStepDto.builder()
                        .id(s.getId())
                        .stepNumber(s.getStepNumber())
                        .approverUserId(s.getApproverUser() != null ? s.getApproverUser().getId() : null)
                        .approverName(s.getApproverUser() != null ? s.getApproverUser().getFirstName() + " " + s.getApproverUser().getLastName() : null)
                        .status(s.getStatus())
                        .comments(s.getComments())
                        .actionTime(s.getActionTime())
                        .build()).collect(Collectors.toList());

        return LeaveApplicationDto.builder()
                .id(a.getId())
                .employeeId(a.getEmployee().getId())
                .employeeName(a.getEmployee().getFirstName() + " " + a.getEmployee().getLastName())
                .employeeCode(a.getEmployee().getEmployeeCode())
                .leaveTypeId(a.getLeaveType().getId())
                .leaveTypeCode(a.getLeaveType().getCode())
                .leaveTypeName(a.getLeaveType().getName())
                .startDate(a.getStartDate())
                .endDate(a.getEndDate())
                .isHalfDay(a.getIsHalfDay())
                .halfDaySession(a.getHalfDaySession())
                .totalDays(a.getTotalDays())
                .reason(a.getReason())
                .attachmentUrl(a.getAttachmentUrl())
                .status(a.getStatus())
                .createdAt(a.getCreatedAt())
                .approvalSteps(steps)
                .build();
    }
}

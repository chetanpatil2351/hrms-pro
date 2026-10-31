package com.hrmspro.modules.leave;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LeaveApprovalStepRepository extends JpaRepository<LeaveApprovalStep, UUID> {

    List<LeaveApprovalStep> findByLeaveApplicationIdOrderByStepNumberAsc(UUID leaveApplicationId);
}

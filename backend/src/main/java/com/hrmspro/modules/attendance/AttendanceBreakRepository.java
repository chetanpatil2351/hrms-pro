package com.hrmspro.modules.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceBreakRepository extends JpaRepository<AttendanceBreak, UUID> {
    Optional<AttendanceBreak> findFirstByAttendanceRecordIdAndEndTimeIsNull(UUID attendanceRecordId);
    List<AttendanceBreak> findByAttendanceRecordIdOrderByStartTimeAsc(UUID attendanceRecordId);
}
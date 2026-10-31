package com.hrmspro.modules.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceEventRepository extends JpaRepository<AttendanceEvent, UUID> {
    Optional<AttendanceEvent> findByIdempotencyKey(String idempotencyKey);
    List<AttendanceEvent> findByAttendanceRecordIdOrderByEventTimeAsc(UUID attendanceRecordId);
}
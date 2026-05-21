package com.liveklass.enrollment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    boolean existsByUserIdAndCourseIdAndStatusIn(Long userId, Long courseId, List<EnrollmentStatus> statuses);

    Page<Enrollment> findByUserId(Long userId, Pageable pageable);

    Optional<Enrollment> findFirstByCourseIdAndStatusOrderByCreatedAtAsc(Long courseId, EnrollmentStatus status);

    Page<Enrollment> findByCourseId(Long courseId, Pageable pageable);

    Optional<Enrollment> findByUserIdAndCourseIdAndStatus(Long userId, Long courseId, EnrollmentStatus status);

    @Query("SELECT COUNT(e) FROM Enrollment e WHERE e.courseId = :courseId AND e.status = 'PENDING' AND e.createdAt < :createdAt")
    long countPendingBefore(@Param("courseId") Long courseId, @Param("createdAt") LocalDateTime createdAt);
}

package com.liveklass.enrollment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface WaitlistRepository extends JpaRepository<Waitlist, Long> {

    Optional<Waitlist> findFirstByCourseIdOrderByCreatedAtAsc(Long courseId);

    boolean existsByUserIdAndCourseId(Long userId, Long courseId);

    Optional<Waitlist> findByUserIdAndCourseId(Long userId, Long courseId);

    long countByCourseIdAndCreatedAtBefore(Long courseId, LocalDateTime createdAt);
}

package com.liveklass.enrollment.dto;

import com.liveklass.enrollment.Enrollment;
import com.liveklass.enrollment.EnrollmentStatus;

import java.time.LocalDateTime;

public record EnrollmentResponse(
        Long id,
        Long userId,
        Long courseId,
        EnrollmentStatus status,
        LocalDateTime confirmedAt,
        LocalDateTime cancelledAt,
        LocalDateTime createdAt
) {
    public static EnrollmentResponse from(Enrollment enrollment) {
        return new EnrollmentResponse(
                enrollment.getId(),
                enrollment.getUserId(),
                enrollment.getCourseId(),
                enrollment.getStatus(),
                enrollment.getConfirmedAt(),
                enrollment.getCancelledAt(),
                enrollment.getCreatedAt()
        );
    }
}

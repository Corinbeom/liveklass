package com.liveclass.course.dto;

import com.liveclass.course.Course;
import com.liveclass.course.CourseStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CourseResponse(
        Long id,
        Long creatorId,
        String title,
        String description,
        BigDecimal price,
        int capacity,
        int enrolledCount,
        LocalDate startDate,
        LocalDate endDate,
        CourseStatus status
) {
    public static CourseResponse from(Course course) {
        return new CourseResponse(
                course.getId(),
                course.getCreatorId(),
                course.getTitle(),
                course.getDescription(),
                course.getPrice(),
                course.getCapacity(),
                course.getEnrolledCount(),
                course.getStartDate(),
                course.getEndDate(),
                course.getStatus()
        );
    }
}

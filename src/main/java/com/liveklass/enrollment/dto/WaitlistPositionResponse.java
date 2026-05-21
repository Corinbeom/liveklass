package com.liveklass.enrollment.dto;

public record WaitlistPositionResponse(
        Long enrollmentId,
        Long courseId,
        long position
) {}

package com.liveklass.enrollment.dto;

import jakarta.validation.constraints.NotNull;

public record EnrollmentRequest(
        @NotNull(message = "강의 ID는 필수입니다.") Long courseId
) {}

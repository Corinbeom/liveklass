package com.liveclass.course.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CourseCreateRequest(
        @NotBlank(message = "제목은 필수입니다.") String title,
        String description,
        @NotNull(message = "가격은 필수입니다.") @DecimalMin(value = "0", message = "가격은 0 이상이어야 합니다.") BigDecimal price,
        @Min(value = 1, message = "정원은 1 이상이어야 합니다.") int capacity,
        @NotNull(message = "시작일은 필수입니다.") LocalDate startDate,
        @NotNull(message = "종료일은 필수입니다.") LocalDate endDate
) {
    @AssertTrue(message = "시작일은 종료일보다 이전이어야 합니다.")
    public boolean isDateRangeValid() {
        if (startDate == null || endDate == null) return true;
        return startDate.isBefore(endDate);
    }
}

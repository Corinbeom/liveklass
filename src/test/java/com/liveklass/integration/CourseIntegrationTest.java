package com.liveklass.integration;

import com.liveklass.course.Course;
import com.liveklass.course.CourseStatus;
import com.liveklass.course.dto.CourseCreateRequest;
import com.liveklass.course.dto.CourseStatusUpdateRequest;
import com.liveklass.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CourseIntegrationTest extends IntegrationTestSupport {

    private CourseCreateRequest defaultRequest() {
        return new CourseCreateRequest("Spring Boot 완전 정복", "설명",
                BigDecimal.valueOf(99000), 30,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31));
    }

    private Course savedOpenCourse(int capacity) {
        Course course = new Course(1L, "테스트 강의", "설명", BigDecimal.valueOf(10000), capacity,
                LocalDate.now(), LocalDate.now().plusMonths(1));
        course.transition(CourseStatus.OPEN);
        return courseRepository.save(course);
    }

    @Test
    @DisplayName("강의를 등록하면 201과 DRAFT 상태를 반환한다")
    void createCourse() throws Exception {
        mockMvc.perform(post("/api/courses")
                        .header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defaultRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.enrolledCount").value(0))
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    @DisplayName("필수 필드 누락 시 400을 반환한다")
    void createCourse_validationFail() throws Exception {
        mockMvc.perform(post("/api/courses")
                        .header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("강의 상태를 OPEN으로 변경할 수 있다")
    void updateStatus_toOpen() throws Exception {
        Course course = courseRepository.save(
                new Course(1L, "테스트", "설명", BigDecimal.valueOf(10000), 30,
                        LocalDate.now(), LocalDate.now().plusMonths(1)));

        mockMvc.perform(patch("/api/courses/" + course.getId() + "/status")
                        .header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CourseStatusUpdateRequest(CourseStatus.OPEN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    @DisplayName("크리에이터가 아닌 사용자가 상태 변경 시 403을 반환한다")
    void updateStatus_unauthorized() throws Exception {
        Course course = courseRepository.save(
                new Course(1L, "테스트", "설명", BigDecimal.valueOf(10000), 30,
                        LocalDate.now(), LocalDate.now().plusMonths(1)));

        mockMvc.perform(patch("/api/courses/" + course.getId() + "/status")
                        .header("X-User-Id", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CourseStatusUpdateRequest(CourseStatus.OPEN))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("존재하지 않는 강의 조회 시 404를 반환한다")
    void findById_notFound() throws Exception {
        mockMvc.perform(get("/api/courses/999")
                        .header("X-User-Id", 1L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COURSE_NOT_FOUND"));
    }

    @Test
    @DisplayName("상태 필터로 강의 목록을 조회할 수 있다")
    void findAll_withStatusFilter() throws Exception {
        savedOpenCourse(30);
        courseRepository.save(new Course(1L, "초안 강의", "설명", BigDecimal.valueOf(10000), 30,
                LocalDate.now(), LocalDate.now().plusMonths(1))); // DRAFT

        mockMvc.perform(get("/api/courses?status=OPEN")
                        .header("X-User-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }
}

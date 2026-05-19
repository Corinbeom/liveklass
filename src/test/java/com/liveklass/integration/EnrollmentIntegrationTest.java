package com.liveklass.integration;

import com.liveklass.course.Course;
import com.liveklass.course.CourseStatus;
import com.liveklass.enrollment.Enrollment;
import com.liveklass.enrollment.dto.EnrollmentRequest;
import com.liveklass.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EnrollmentIntegrationTest extends IntegrationTestSupport {

    private Course openCourse(int capacity) {
        Course course = new Course(1L, "테스트 강의", "설명", BigDecimal.valueOf(10000), capacity,
                LocalDate.now(), LocalDate.now().plusMonths(1));
        course.transition(CourseStatus.OPEN);
        return courseRepository.save(course);
    }

    @Test
    @DisplayName("OPEN 강의에 수강 신청하면 201과 PENDING 상태를 반환한다")
    void enroll_success() throws Exception {
        Course course = openCourse(30);

        mockMvc.perform(post("/api/enrollments")
                        .header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EnrollmentRequest(course.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("DRAFT 강의에 수강 신청 시 400을 반환한다")
    void enroll_draftCourse() throws Exception {
        Course course = courseRepository.save(
                new Course(1L, "테스트", "설명", BigDecimal.valueOf(10000), 30,
                        LocalDate.now(), LocalDate.now().plusMonths(1)));

        mockMvc.perform(post("/api/enrollments")
                        .header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EnrollmentRequest(course.getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COURSE_NOT_OPEN"));
    }

    @Test
    @DisplayName("같은 강의에 중복 신청 시 409를 반환한다")
    void enroll_duplicate() throws Exception {
        Course course = openCourse(30);
        enrollmentRepository.save(new Enrollment(1L, course.getId()));

        mockMvc.perform(post("/api/enrollments")
                        .header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EnrollmentRequest(course.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_ENROLLED"));
    }

    @Test
    @DisplayName("수강 신청 후 confirm하면 CONFIRMED가 된다")
    void confirm_success() throws Exception {
        Course course = openCourse(30);
        Enrollment enrollment = enrollmentRepository.save(new Enrollment(1L, course.getId()));

        mockMvc.perform(post("/api/enrollments/" + enrollment.getId() + "/confirm")
                        .header("X-User-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedAt").isNotEmpty());
    }

    @Test
    @DisplayName("정원이 꽉 찬 강의 confirm 시 409를 반환한다")
    void confirm_courseFull() throws Exception {
        Course course = openCourse(1);

        Enrollment confirmed = enrollmentRepository.save(new Enrollment(1L, course.getId()));
        mockMvc.perform(post("/api/enrollments/" + confirmed.getId() + "/confirm")
                .header("X-User-Id", 1L))
                .andExpect(status().isOk());

        Enrollment waiting = enrollmentRepository.save(new Enrollment(2L, course.getId()));

        mockMvc.perform(post("/api/enrollments/" + waiting.getId() + "/confirm")
                        .header("X-User-Id", 2L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COURSE_FULL"));
    }

    @Test
    @DisplayName("CONFIRMED 신청을 취소하면 CANCELLED가 되고 enrolledCount가 감소한다")
    void cancel_confirmed() throws Exception {
        Course course = openCourse(30);
        Enrollment enrollment = enrollmentRepository.save(new Enrollment(1L, course.getId()));

        mockMvc.perform(post("/api/enrollments/" + enrollment.getId() + "/confirm")
                .header("X-User-Id", 1L));

        mockMvc.perform(post("/api/enrollments/" + enrollment.getId() + "/cancel")
                        .header("X-User-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        Course updated = courseRepository.findById(course.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.getEnrolledCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("내 수강 신청 목록을 페이지네이션으로 조회할 수 있다")
    void findMyEnrollments() throws Exception {
        Course course = openCourse(30);
        enrollmentRepository.save(new Enrollment(1L, course.getId()));

        mockMvc.perform(get("/api/enrollments/me")
                        .header("X-User-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].userId").value(1));
    }
}

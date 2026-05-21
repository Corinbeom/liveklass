package com.liveklass.integration;

import com.liveklass.course.Course;
import com.liveklass.course.CourseStatus;
import com.liveklass.enrollment.Enrollment;
import com.liveklass.enrollment.EnrollmentStatus;
import com.liveklass.enrollment.Waitlist;
import com.liveklass.enrollment.dto.EnrollmentRequest;
import com.liveklass.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EnrollmentIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Course openCourse(int capacity) {
        Course course = new Course(1L, "테스트 강의", "설명", BigDecimal.valueOf(10000), capacity,
                LocalDate.now(), LocalDate.now().plusMonths(1));
        course.transition(CourseStatus.OPEN);
        return courseRepository.save(course);
    }

    // ─── 수강 신청 ───────────────────────────────────────────────

    @Test
    @DisplayName("자리가 있는 OPEN 강의에 수강 신청하면 201과 PENDING을 반환한다")
    void enroll_success() throws Exception {
        Course course = openCourse(30);

        mockMvc.perform(post("/api/enrollments")
                        .header("X-User-Id", 2L)
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
                        .header("X-User-Id", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EnrollmentRequest(course.getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COURSE_NOT_OPEN"));
    }

    @Test
    @DisplayName("크리에이터가 본인 강의에 수강 신청 시 400을 반환한다")
    void enroll_creatorCannotEnroll() throws Exception {
        Course course = openCourse(30);

        mockMvc.perform(post("/api/enrollments")
                        .header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EnrollmentRequest(course.getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CREATOR_CANNOT_ENROLL"));
    }

    @Test
    @DisplayName("같은 강의에 중복 신청 시 409를 반환한다")
    void enroll_duplicate() throws Exception {
        Course course = openCourse(30);
        enrollmentRepository.save(new Enrollment(2L, course.getId()));

        mockMvc.perform(post("/api/enrollments")
                        .header("X-User-Id", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EnrollmentRequest(course.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_ENROLLED"));
    }

    @Test
    @DisplayName("정원이 가득 찬 강의에 수강 신청 시 409를 반환한다")
    void enroll_courseFull() throws Exception {
        Course course = openCourse(1);
        enrollmentRepository.save(new Enrollment(2L, course.getId())); // PENDING이 자리 점유

        mockMvc.perform(post("/api/enrollments")
                        .header("X-User-Id", 3L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EnrollmentRequest(course.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COURSE_FULL"));
    }

    // ─── 결제 확정 ───────────────────────────────────────────────

    @Test
    @DisplayName("수강 신청 후 confirm하면 CONFIRMED가 된다")
    void confirm_success() throws Exception {
        Course course = openCourse(30);
        Enrollment enrollment = enrollmentRepository.save(new Enrollment(2L, course.getId()));

        mockMvc.perform(post("/api/enrollments/" + enrollment.getId() + "/confirm")
                        .header("X-User-Id", 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedAt").isNotEmpty());
    }

    // ─── 수강 취소 ───────────────────────────────────────────────

    @Test
    @DisplayName("CONFIRMED 신청을 취소하면 CANCELLED가 되고 enrolledCount가 감소한다")
    void cancel_confirmed() throws Exception {
        Course course = openCourse(30);
        Enrollment enrollment = enrollmentRepository.save(new Enrollment(2L, course.getId()));

        mockMvc.perform(post("/api/enrollments/" + enrollment.getId() + "/confirm")
                .header("X-User-Id", 2L));

        mockMvc.perform(post("/api/enrollments/" + enrollment.getId() + "/cancel")
                        .header("X-User-Id", 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        assertThat(courseRepository.findById(course.getId()).orElseThrow().getEnrolledCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("CONFIRMED 취소 시 대기열 1번이 PENDING으로 승격된다")
    void cancel_promotesWaitlistToPending() throws Exception {
        Course course = openCourse(1);
        Enrollment enrollment = enrollmentRepository.save(new Enrollment(2L, course.getId()));
        mockMvc.perform(post("/api/enrollments/" + enrollment.getId() + "/confirm")
                .header("X-User-Id", 2L));

        Waitlist waiter = waitlistRepository.save(new Waitlist(course.getId(), 3L));

        mockMvc.perform(post("/api/enrollments/" + enrollment.getId() + "/cancel")
                        .header("X-User-Id", 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        // 대기자가 PENDING enrollment로 승격되었는지 확인
        assertThat(enrollmentRepository.existsByUserIdAndCourseIdAndStatusIn(
                3L, course.getId(), java.util.List.of(EnrollmentStatus.PENDING))).isTrue();
        // Waitlist에서 제거되었는지 확인
        assertThat(waitlistRepository.findById(waiter.getId())).isEmpty();
    }

    @Test
    @DisplayName("본인 신청이 아닌 경우 취소 시 403을 반환한다")
    void cancel_unauthorized() throws Exception {
        Course course = openCourse(30);
        Enrollment enrollment = enrollmentRepository.save(new Enrollment(2L, course.getId()));

        mockMvc.perform(post("/api/enrollments/" + enrollment.getId() + "/cancel")
                        .header("X-User-Id", 3L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("결제 후 7일이 초과된 경우 취소 시 409를 반환한다")
    void cancel_expiredPeriod() throws Exception {
        Course course = openCourse(30);
        Enrollment enrollment = enrollmentRepository.save(new Enrollment(2L, course.getId()));

        mockMvc.perform(post("/api/enrollments/" + enrollment.getId() + "/confirm")
                .header("X-User-Id", 2L));

        jdbcTemplate.update("UPDATE enrollments SET confirmed_at = ? WHERE id = ?",
                LocalDateTime.now().minusDays(8), enrollment.getId());

        mockMvc.perform(post("/api/enrollments/" + enrollment.getId() + "/cancel")
                        .header("X-User-Id", 2L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CANCEL_PERIOD_EXPIRED"));
    }

    // ─── 대기열 ──────────────────────────────────────────────────

    @Test
    @DisplayName("정원이 가득 찬 강의에 대기열 등록 시 201과 순번을 반환한다")
    void joinWaitlist_success() throws Exception {
        Course course = openCourse(1);
        enrollmentRepository.save(new Enrollment(2L, course.getId())); // 정원 차지

        mockMvc.perform(post("/api/courses/" + course.getId() + "/waitlist")
                        .header("X-User-Id", 3L))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.position").value(1));
    }

    @Test
    @DisplayName("이미 대기열에 있는 경우 중복 등록 시 409를 반환한다")
    void joinWaitlist_duplicate() throws Exception {
        Course course = openCourse(1);
        enrollmentRepository.save(new Enrollment(2L, course.getId()));
        waitlistRepository.save(new Waitlist(course.getId(), 3L));

        mockMvc.perform(post("/api/courses/" + course.getId() + "/waitlist")
                        .header("X-User-Id", 3L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_IN_WAITLIST"));
    }

    @Test
    @DisplayName("대기 순번을 조회할 수 있다")
    void getWaitlistPosition() throws Exception {
        Course course = openCourse(1);
        enrollmentRepository.save(new Enrollment(2L, course.getId()));
        waitlistRepository.save(new Waitlist(course.getId(), 3L));
        waitlistRepository.save(new Waitlist(course.getId(), 4L));

        mockMvc.perform(get("/api/courses/" + course.getId() + "/waitlist/me")
                        .header("X-User-Id", 4L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.position").value(2));
    }

    @Test
    @DisplayName("대기열에서 이탈하면 Waitlist에서 삭제된다")
    void leaveWaitlist() throws Exception {
        Course course = openCourse(1);
        enrollmentRepository.save(new Enrollment(2L, course.getId()));
        waitlistRepository.save(new Waitlist(course.getId(), 3L));

        mockMvc.perform(delete("/api/courses/" + course.getId() + "/waitlist/me")
                        .header("X-User-Id", 3L))
                .andExpect(status().isOk());

        assertThat(waitlistRepository.existsByUserIdAndCourseId(3L, course.getId())).isFalse();
    }

    // ─── 목록 조회 ───────────────────────────────────────────────

    @Test
    @DisplayName("내 수강 신청 목록을 페이지네이션으로 조회할 수 있다")
    void findMyEnrollments() throws Exception {
        Course course = openCourse(30);
        enrollmentRepository.save(new Enrollment(2L, course.getId()));

        mockMvc.perform(get("/api/enrollments/me")
                        .header("X-User-Id", 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].userId").value(2));
    }
}

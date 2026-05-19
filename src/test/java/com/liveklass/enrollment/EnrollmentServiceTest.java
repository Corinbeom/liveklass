package com.liveklass.enrollment;

import com.liveklass.common.BusinessException;
import com.liveklass.common.ErrorCode;
import com.liveklass.course.Course;
import com.liveklass.course.CourseRepository;
import com.liveklass.course.CourseStatus;
import com.liveklass.enrollment.dto.EnrollmentRequest;
import com.liveklass.enrollment.dto.EnrollmentResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private EnrollmentService enrollmentService;

    private Course openCourse(int capacity) {
        Course course = new Course(1L, "테스트 강의", "설명", BigDecimal.valueOf(10000), capacity,
                LocalDate.now(), LocalDate.now().plusMonths(1));
        course.transition(CourseStatus.OPEN);
        return course;
    }

    @Test
    @DisplayName("OPEN 강의에 수강 신청하면 PENDING 상태로 생성된다")
    void enroll_success() {
        Course course = openCourse(30);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.existsByUserIdAndCourseIdAndStatusIn(any(), any(), any())).thenReturn(false);
        when(enrollmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EnrollmentResponse response = enrollmentService.enroll(1L, new EnrollmentRequest(1L));

        assertThat(response.status()).isEqualTo(EnrollmentStatus.PENDING);
    }

    @Test
    @DisplayName("DRAFT 강의에 수강 신청 시 예외가 발생한다")
    void enroll_courseNotOpen() {
        Course course = new Course(1L, "테스트", "설명", BigDecimal.valueOf(10000), 30,
                LocalDate.now(), LocalDate.now().plusMonths(1));
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> enrollmentService.enroll(1L, new EnrollmentRequest(1L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COURSE_NOT_OPEN);
    }

    @Test
    @DisplayName("이미 신청한 강의에 중복 신청 시 예외가 발생한다")
    void enroll_alreadyEnrolled() {
        Course course = openCourse(30);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.existsByUserIdAndCourseIdAndStatusIn(any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> enrollmentService.enroll(1L, new EnrollmentRequest(1L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALREADY_ENROLLED);
    }

    @Test
    @DisplayName("정상적으로 confirm하면 CONFIRMED가 되고 enrolledCount가 증가한다")
    void confirm_success() {
        Course course = openCourse(30);
        Enrollment enrollment = new Enrollment(1L, 1L);
        when(enrollmentRepository.findById(1L)).thenReturn(Optional.of(enrollment));
        when(courseRepository.findByIdWithLock(1L)).thenReturn(Optional.of(course));

        EnrollmentResponse response = enrollmentService.confirm(1L, 1L);

        assertThat(response.status()).isEqualTo(EnrollmentStatus.CONFIRMED);
        assertThat(course.getEnrolledCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("정원이 꽉 찬 강의 confirm 시 예외가 발생한다")
    void confirm_courseFull() {
        Course course = openCourse(1);
        course.increaseEnrolledCount();
        Enrollment enrollment = new Enrollment(1L, 1L);
        when(enrollmentRepository.findById(1L)).thenReturn(Optional.of(enrollment));
        when(courseRepository.findByIdWithLock(1L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> enrollmentService.confirm(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COURSE_FULL);
    }

    @Test
    @DisplayName("본인 신청이 아닌 경우 confirm 시 예외가 발생한다")
    void confirm_unauthorized() {
        Enrollment enrollment = new Enrollment(1L, 1L);
        when(enrollmentRepository.findById(1L)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> enrollmentService.confirm(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("CONFIRMED 취소 시 enrolledCount가 감소하고 대기자가 자동 승격된다")
    void cancel_promotesWaitlist() {
        Course course = openCourse(1);
        course.increaseEnrolledCount();

        Enrollment confirmed = new Enrollment(2L, 1L);
        confirmed.confirm();

        Enrollment waiting = new Enrollment(3L, 1L);

        when(enrollmentRepository.findById(1L)).thenReturn(Optional.of(confirmed));
        when(courseRepository.findByIdWithLock(1L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findFirstByCourseIdAndStatusOrderByCreatedAtAsc(1L, EnrollmentStatus.PENDING))
                .thenReturn(Optional.of(waiting));

        enrollmentService.cancel(1L, 2L);

        assertThat(confirmed.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(waiting.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
        assertThat(course.getEnrolledCount()).isEqualTo(1);
    }
}

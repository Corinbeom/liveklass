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

    @Mock
    private WaitlistRepository waitlistRepository;

    @InjectMocks
    private EnrollmentService enrollmentService;

    private Course openCourse(int capacity) {
        Course course = new Course(1L, "테스트 강의", "설명", BigDecimal.valueOf(10000), capacity,
                LocalDate.now(), LocalDate.now().plusMonths(1));
        course.transition(CourseStatus.OPEN);
        return course;
    }

    @Test
    @DisplayName("OPEN 강의에 자리가 있으면 PENDING으로 수강 신청된다")
    void enroll_success() {
        Course course = openCourse(30);
        when(courseRepository.findByIdWithLock(1L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.existsByUserIdAndCourseIdAndStatusIn(any(), any(), any())).thenReturn(false);
        when(waitlistRepository.existsByUserIdAndCourseId(any(), any())).thenReturn(false);
        when(enrollmentRepository.countByCourseIdAndStatus(any(), any())).thenReturn(0L);
        when(enrollmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EnrollmentResponse response = enrollmentService.enroll(2L, new EnrollmentRequest(1L));

        assertThat(response.status()).isEqualTo(EnrollmentStatus.PENDING);
    }

    @Test
    @DisplayName("DRAFT 강의에 수강 신청 시 예외가 발생한다")
    void enroll_courseNotOpen() {
        Course course = new Course(1L, "테스트", "설명", BigDecimal.valueOf(10000), 30,
                LocalDate.now(), LocalDate.now().plusMonths(1));
        when(courseRepository.findByIdWithLock(1L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> enrollmentService.enroll(2L, new EnrollmentRequest(1L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COURSE_NOT_OPEN);
    }

    @Test
    @DisplayName("크리에이터가 본인 강의에 수강 신청 시 예외가 발생한다")
    void enroll_creatorCannotEnroll() {
        Course course = openCourse(30);
        when(courseRepository.findByIdWithLock(1L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> enrollmentService.enroll(1L, new EnrollmentRequest(1L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CREATOR_CANNOT_ENROLL);
    }

    @Test
    @DisplayName("이미 신청한 강의에 중복 신청 시 예외가 발생한다")
    void enroll_alreadyEnrolled() {
        Course course = openCourse(30);
        when(courseRepository.findByIdWithLock(1L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.existsByUserIdAndCourseIdAndStatusIn(any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> enrollmentService.enroll(2L, new EnrollmentRequest(1L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALREADY_ENROLLED);
    }

    @Test
    @DisplayName("정원이 꽉 찬 강의에 수강 신청 시 예외가 발생한다")
    void enroll_courseFull() {
        Course course = openCourse(1);
        course.increaseEnrolledCount(); // enrolledCount = 1 = capacity
        when(courseRepository.findByIdWithLock(1L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.existsByUserIdAndCourseIdAndStatusIn(any(), any(), any())).thenReturn(false);
        when(waitlistRepository.existsByUserIdAndCourseId(any(), any())).thenReturn(false);
        when(enrollmentRepository.countByCourseIdAndStatus(any(), any())).thenReturn(0L);

        assertThatThrownBy(() -> enrollmentService.enroll(2L, new EnrollmentRequest(1L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COURSE_FULL);
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
    @DisplayName("CONFIRMED 취소 시 CANCELLED가 되고 enrolledCount가 감소한다")
    void cancel_decreasesEnrolledCount() {
        Course course = openCourse(1);
        course.increaseEnrolledCount();

        Enrollment confirmed = new Enrollment(2L, 1L);
        confirmed.confirm();

        when(enrollmentRepository.findById(1L)).thenReturn(Optional.of(confirmed));
        when(courseRepository.findByIdWithLock(1L)).thenReturn(Optional.of(course));
        when(waitlistRepository.findFirstByCourseIdOrderByCreatedAtAsc(any())).thenReturn(Optional.empty());

        enrollmentService.cancel(1L, 2L);

        assertThat(confirmed.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(course.getEnrolledCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("CONFIRMED 취소 시 대기열 1번이 PENDING으로 승격된다")
    void cancel_promotesWaitlist() {
        Course course = openCourse(1);
        course.increaseEnrolledCount();

        Enrollment confirmed = new Enrollment(2L, 1L);
        confirmed.confirm();

        Waitlist waiter = new Waitlist(1L, 3L);

        when(enrollmentRepository.findById(1L)).thenReturn(Optional.of(confirmed));
        when(courseRepository.findByIdWithLock(1L)).thenReturn(Optional.of(course));
        when(waitlistRepository.findFirstByCourseIdOrderByCreatedAtAsc(1L)).thenReturn(Optional.of(waiter));
        when(enrollmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        enrollmentService.cancel(1L, 2L);

        assertThat(confirmed.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
    }
}

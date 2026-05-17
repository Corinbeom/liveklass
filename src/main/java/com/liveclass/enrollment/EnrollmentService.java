package com.liveclass.enrollment;

import com.liveclass.common.BusinessException;
import com.liveclass.common.ErrorCode;
import com.liveclass.course.Course;
import com.liveclass.course.CourseRepository;
import com.liveclass.course.CourseStatus;
import com.liveclass.enrollment.dto.EnrollmentRequest;
import com.liveclass.enrollment.dto.EnrollmentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;

    public EnrollmentService(EnrollmentRepository enrollmentRepository, CourseRepository courseRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional
    public EnrollmentResponse enroll(Long userId, EnrollmentRequest request) {
        Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));

        if (course.getStatus() != CourseStatus.OPEN) {
            throw new BusinessException(ErrorCode.COURSE_NOT_OPEN);
        }

        boolean alreadyEnrolled = enrollmentRepository.existsByUserIdAndCourseIdAndStatusIn(
                userId, course.getId(), List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED)
        );
        if (alreadyEnrolled) {
            throw new BusinessException(ErrorCode.ALREADY_ENROLLED);
        }

        Enrollment enrollment = new Enrollment(userId, course.getId());
        return EnrollmentResponse.from(enrollmentRepository.save(enrollment));
    }

    @Transactional
    public EnrollmentResponse confirm(Long enrollmentId, Long userId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));
        enrollment.validateOwner(userId);

        Course course = courseRepository.findByIdWithLock(enrollment.getCourseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));

        if (course.isFull()) {
            throw new BusinessException(ErrorCode.COURSE_FULL);
        }

        enrollment.confirm();
        course.increaseEnrolledCount();

        return EnrollmentResponse.from(enrollment);
    }

    @Transactional
    public EnrollmentResponse cancel(Long enrollmentId, Long userId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));
        enrollment.validateOwner(userId);

        boolean wasConfirmed = enrollment.getStatus() == EnrollmentStatus.CONFIRMED;
        enrollment.cancel();

        if (wasConfirmed) {
            Course course = courseRepository.findByIdWithLock(enrollment.getCourseId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
            course.decreaseEnrolledCount();

            enrollmentRepository.findFirstByCourseIdAndStatusOrderByCreatedAtAsc(
                    course.getId(), EnrollmentStatus.PENDING
            ).ifPresent(waiting -> {
                waiting.confirm();
                course.increaseEnrolledCount();
            });
        }

        return EnrollmentResponse.from(enrollment);
    }

    public Page<EnrollmentResponse> findMyEnrollments(Long userId, Pageable pageable) {
        return enrollmentRepository.findByUserId(userId, pageable)
                .map(EnrollmentResponse::from);
    }

    public Page<EnrollmentResponse> findByCourse(Long courseId, Long requesterId, Pageable pageable) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        course.validateCreator(requesterId);

        return enrollmentRepository.findByCourseId(courseId, pageable)
                .map(EnrollmentResponse::from);
    }
}

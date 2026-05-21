package com.liveklass.enrollment;

import com.liveklass.common.BusinessException;
import com.liveklass.common.ErrorCode;
import com.liveklass.course.Course;
import com.liveklass.course.CourseRepository;
import com.liveklass.course.CourseStatus;
import com.liveklass.enrollment.dto.EnrollmentRequest;
import com.liveklass.enrollment.dto.EnrollmentResponse;
import com.liveklass.enrollment.dto.WaitlistPositionResponse;
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
    private final WaitlistRepository waitlistRepository;

    public EnrollmentService(EnrollmentRepository enrollmentRepository,
                             CourseRepository courseRepository,
                             WaitlistRepository waitlistRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
        this.waitlistRepository = waitlistRepository;
    }

    @Transactional
    public EnrollmentResponse enroll(Long userId, EnrollmentRequest request) {
        Course course = courseRepository.findByIdWithLock(request.courseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));

        if (course.getStatus() != CourseStatus.OPEN) {
            throw new BusinessException(ErrorCode.COURSE_NOT_OPEN);
        }

        if (course.getCreatorId().equals(userId)) {
            throw new BusinessException(ErrorCode.CREATOR_CANNOT_ENROLL);
        }

        boolean alreadyEnrolled = enrollmentRepository.existsByUserIdAndCourseIdAndStatusIn(
                userId, course.getId(), List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED)
        );
        if (alreadyEnrolled) {
            throw new BusinessException(ErrorCode.ALREADY_ENROLLED);
        }

        if (waitlistRepository.existsByUserIdAndCourseId(userId, course.getId())) {
            throw new BusinessException(ErrorCode.ALREADY_IN_WAITLIST);
        }

        long pendingCount = enrollmentRepository.countByCourseIdAndStatus(course.getId(), EnrollmentStatus.PENDING);
        if (course.getEnrolledCount() + pendingCount >= course.getCapacity()) {
            throw new BusinessException(ErrorCode.COURSE_FULL);
        }

        Enrollment enrollment = new Enrollment(userId, course.getId());
        return EnrollmentResponse.from(enrollmentRepository.save(enrollment));
    }

    @Transactional
    public WaitlistPositionResponse joinWaitlist(Long courseId, Long userId) {
        Course course = courseRepository.findByIdWithLock(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));

        if (course.getStatus() != CourseStatus.OPEN) {
            throw new BusinessException(ErrorCode.COURSE_NOT_OPEN);
        }

        if (course.getCreatorId().equals(userId)) {
            throw new BusinessException(ErrorCode.CREATOR_CANNOT_ENROLL);
        }

        boolean alreadyEnrolled = enrollmentRepository.existsByUserIdAndCourseIdAndStatusIn(
                userId, courseId, List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED)
        );
        if (alreadyEnrolled) {
            throw new BusinessException(ErrorCode.ALREADY_ENROLLED);
        }

        if (waitlistRepository.existsByUserIdAndCourseId(userId, courseId)) {
            throw new BusinessException(ErrorCode.ALREADY_IN_WAITLIST);
        }

        long pendingCount = enrollmentRepository.countByCourseIdAndStatus(courseId, EnrollmentStatus.PENDING);
        if (course.getEnrolledCount() + pendingCount < course.getCapacity()) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FULL);
        }

        Waitlist waitlist = waitlistRepository.save(new Waitlist(courseId, userId));
        long position = waitlistRepository.countByCourseIdAndCreatedAtBefore(courseId, waitlist.getCreatedAt()) + 1;
        return WaitlistPositionResponse.of(waitlist, position);
    }

    @Transactional
    public EnrollmentResponse confirm(Long enrollmentId, Long userId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));
        enrollment.validateOwner(userId);

        Course course = courseRepository.findByIdWithLock(enrollment.getCourseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));

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

        Course course = courseRepository.findByIdWithLock(enrollment.getCourseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));

        if (wasConfirmed) {
            course.decreaseEnrolledCount();
        }

        // CONFIRMED/PENDING 취소 모두 빈 자리를 대기열 1번에게 승격
        waitlistRepository.findFirstByCourseIdOrderByCreatedAtAsc(enrollment.getCourseId())
                .ifPresent(waitlist -> {
                    enrollmentRepository.save(new Enrollment(waitlist.getUserId(), waitlist.getCourseId()));
                    waitlistRepository.delete(waitlist);
                });

        return EnrollmentResponse.from(enrollment);
    }

    @Transactional
    public void leaveWaitlist(Long courseId, Long userId) {
        Waitlist waitlist = waitlistRepository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAITLIST_NOT_FOUND));
        waitlistRepository.delete(waitlist);
    }

    public WaitlistPositionResponse getWaitlistPosition(Long courseId, Long userId) {
        Waitlist waitlist = waitlistRepository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAITLIST_NOT_FOUND));

        long position = waitlistRepository.countByCourseIdAndCreatedAtBefore(courseId, waitlist.getCreatedAt()) + 1;
        return WaitlistPositionResponse.of(waitlist, position);
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

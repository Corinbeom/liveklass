package com.liveklass.course;

import com.liveklass.common.BusinessException;
import com.liveklass.common.ErrorCode;
import com.liveklass.course.dto.CourseCreateRequest;
import com.liveklass.course.dto.CourseResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courseRepository;

    public CourseService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Transactional
    public CourseResponse create(Long creatorId, CourseCreateRequest request) {
        Course course = new Course(
                creatorId,
                request.title(),
                request.description(),
                request.price(),
                request.capacity(),
                request.startDate(),
                request.endDate()
        );
        return CourseResponse.from(courseRepository.save(course));
    }

    public List<CourseResponse> findAll(CourseStatus status) {
        List<Course> courses = status != null
                ? courseRepository.findByStatus(status)
                : courseRepository.findAll();
        return courses.stream().map(CourseResponse::from).toList();
    }

    public CourseResponse findById(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        return CourseResponse.from(course);
    }

    @Transactional
    public CourseResponse updateStatus(Long courseId, Long userId, CourseStatus newStatus) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        course.validateCreator(userId);
        course.transition(newStatus);
        return CourseResponse.from(course);
    }
}

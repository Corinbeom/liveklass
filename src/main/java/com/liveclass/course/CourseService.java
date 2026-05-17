package com.liveclass.course;

import com.liveclass.common.BusinessException;
import com.liveclass.common.ErrorCode;
import com.liveclass.course.dto.CourseCreateRequest;
import com.liveclass.course.dto.CourseResponse;
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

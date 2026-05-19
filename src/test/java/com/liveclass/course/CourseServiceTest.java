package com.liveclass.course;

import com.liveclass.common.BusinessException;
import com.liveclass.common.ErrorCode;
import com.liveclass.course.dto.CourseCreateRequest;
import com.liveclass.course.dto.CourseResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private CourseService courseService;

    private Course createCourse(Long creatorId) {
        return new Course(creatorId, "테스트 강의", "설명", BigDecimal.valueOf(99000), 30,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31));
    }

    @Test
    @DisplayName("강의를 등록하면 DRAFT 상태로 생성된다")
    void create_success() {
        CourseCreateRequest request = new CourseCreateRequest(
                "테스트 강의", "설명", BigDecimal.valueOf(99000), 30,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31)
        );
        when(courseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CourseResponse response = courseService.create(1L, request);

        assertThat(response.status()).isEqualTo(CourseStatus.DRAFT);
        assertThat(response.enrolledCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("존재하지 않는 강의 조회 시 예외가 발생한다")
    void findById_notFound() {
        when(courseRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.findById(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COURSE_NOT_FOUND);
    }

    @Test
    @DisplayName("DRAFT 상태에서 OPEN으로 전이할 수 있다")
    void updateStatus_draftToOpen() {
        Course course = createCourse(1L);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        CourseResponse response = courseService.updateStatus(1L, 1L, CourseStatus.OPEN);

        assertThat(response.status()).isEqualTo(CourseStatus.OPEN);
    }

    @Test
    @DisplayName("DRAFT에서 CLOSED로 직접 전이 시 예외가 발생한다")
    void updateStatus_invalidTransition() {
        Course course = createCourse(1L);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.updateStatus(1L, 1L, CourseStatus.CLOSED))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("크리에이터가 아닌 사용자가 상태 변경 시 예외가 발생한다")
    void updateStatus_unauthorized() {
        Course course = createCourse(1L);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.updateStatus(1L, 2L, CourseStatus.OPEN))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}

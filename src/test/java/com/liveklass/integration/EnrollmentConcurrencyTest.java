package com.liveklass.integration;

import com.liveklass.common.BusinessException;
import com.liveklass.common.ErrorCode;
import com.liveklass.course.Course;
import com.liveklass.course.CourseStatus;
import com.liveklass.enrollment.Enrollment;
import com.liveklass.enrollment.EnrollmentService;
import com.liveklass.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class EnrollmentConcurrencyTest extends IntegrationTestSupport {

    @Autowired
    private EnrollmentService enrollmentService;

    @Test
    @DisplayName("정원 3명인 강의에 10명이 동시에 confirm 요청하면 3명만 성공한다")
    void concurrentConfirm() throws InterruptedException {
        // given
        int capacity = 3;
        int threadCount = 10;

        Course course = new Course(1L, "동시성 테스트 강의", "설명", BigDecimal.valueOf(10000), capacity,
                LocalDate.now(), LocalDate.now().plusMonths(1));
        course.transition(CourseStatus.OPEN);
        courseRepository.save(course);

        List<Long> enrollmentIds = new ArrayList<>();
        for (long i = 1; i <= threadCount; i++) {
            enrollmentIds.add(enrollmentRepository.save(new Enrollment(i, course.getId())).getId());
        }

        // when
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    enrollmentService.confirm(enrollmentIds.get(index), index + 1L);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.COURSE_FULL) {
                        failCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(capacity);
        assertThat(failCount.get()).isEqualTo(threadCount - capacity);

        Course updated = courseRepository.findById(course.getId()).orElseThrow();
        assertThat(updated.getEnrolledCount()).isEqualTo(capacity);
    }
}

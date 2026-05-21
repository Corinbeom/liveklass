package com.liveklass.integration;

import com.liveklass.common.BusinessException;
import com.liveklass.common.ErrorCode;
import com.liveklass.course.Course;
import com.liveklass.course.CourseStatus;
import com.liveklass.enrollment.EnrollmentService;
import com.liveklass.enrollment.EnrollmentStatus;
import com.liveklass.enrollment.dto.EnrollmentRequest;
import com.liveklass.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class EnrollmentConcurrencyTest extends IntegrationTestSupport {

    @Autowired
    private EnrollmentService enrollmentService;

    @Test
    @DisplayName("정원 3명인 강의에 10명이 동시에 수강 신청하면 3명만 PENDING으로 성공한다")
    void concurrentEnroll() throws InterruptedException {
        int capacity = 3;
        int threadCount = 10;

        Course course = new Course(99L, "동시성 테스트 강의", "설명", BigDecimal.valueOf(10000), capacity,
                LocalDate.now(), LocalDate.now().plusMonths(1));
        course.transition(CourseStatus.OPEN);
        courseRepository.save(course);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger fullCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (long userId = 2; userId <= threadCount + 1; userId++) {
            final long uid = userId;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    enrollmentService.enroll(uid, new EnrollmentRequest(course.getId()));
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.COURSE_FULL) {
                        fullCount.incrementAndGet();
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

        assertThat(successCount.get()).isEqualTo(capacity);
        assertThat(fullCount.get()).isEqualTo(threadCount - capacity);

        long pendingCount = enrollmentRepository.countByCourseIdAndStatus(course.getId(), EnrollmentStatus.PENDING);
        assertThat(pendingCount).isEqualTo(capacity);
    }
}

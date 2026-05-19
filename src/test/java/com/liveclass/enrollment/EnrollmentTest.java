package com.liveclass.enrollment;

import com.liveclass.common.BusinessException;
import com.liveclass.common.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class EnrollmentTest {

    @Test
    @DisplayName("PENDING 상태의 신청을 confirm하면 CONFIRMED가 된다")
    void confirm_success() {
        Enrollment enrollment = new Enrollment(1L, 1L);

        enrollment.confirm();

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
        assertThat(enrollment.getConfirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("CONFIRMED 상태를 다시 confirm하면 예외가 발생한다")
    void confirm_alreadyConfirmed() {
        Enrollment enrollment = new Enrollment(1L, 1L);
        enrollment.confirm();

        assertThatThrownBy(enrollment::confirm)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("PENDING 상태의 신청을 취소할 수 있다")
    void cancel_pending() {
        Enrollment enrollment = new Enrollment(1L, 1L);

        enrollment.cancel();

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(enrollment.getCancelledAt()).isNotNull();
    }

    @Test
    @DisplayName("결제 후 7일 이내 CONFIRMED 신청을 취소할 수 있다")
    void cancel_confirmedWithinPeriod() {
        Enrollment enrollment = new Enrollment(1L, 1L);
        enrollment.confirm();

        enrollment.cancel();

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
    }

    @Test
    @DisplayName("결제 후 7일이 초과된 경우 취소할 수 없다")
    void cancel_expiredPeriod() throws Exception {
        Enrollment enrollment = new Enrollment(1L, 1L);
        enrollment.confirm();

        Field field = Enrollment.class.getDeclaredField("confirmedAt");
        field.setAccessible(true);
        field.set(enrollment, LocalDateTime.now().minusDays(8));

        assertThatThrownBy(enrollment::cancel)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CANCEL_PERIOD_EXPIRED);
    }

    @Test
    @DisplayName("이미 취소된 신청을 다시 취소하면 예외가 발생한다")
    void cancel_alreadyCancelled() {
        Enrollment enrollment = new Enrollment(1L, 1L);
        enrollment.cancel();

        assertThatThrownBy(enrollment::cancel)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
    }
}

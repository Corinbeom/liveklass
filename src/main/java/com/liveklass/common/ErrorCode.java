package com.liveklass.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // Course
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "강의를 찾을 수 없습니다."),
    COURSE_NOT_OPEN(HttpStatus.BAD_REQUEST, "모집 중인 강의가 아닙니다."),
    COURSE_FULL(HttpStatus.CONFLICT, "수강 정원이 초과되었습니다."),
    INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "유효하지 않은 상태 전이입니다."),

    // Enrollment
    ENROLLMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "수강 신청을 찾을 수 없습니다."),
    ALREADY_ENROLLED(HttpStatus.CONFLICT, "이미 신청한 강의입니다."),
    CANCEL_PERIOD_EXPIRED(HttpStatus.CONFLICT, "취소 가능 기간이 지났습니다."),

    // Auth
    UNAUTHORIZED(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    CREATOR_CANNOT_ENROLL(HttpStatus.BAD_REQUEST, "자신이 개설한 강의에는 수강 신청할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }
}

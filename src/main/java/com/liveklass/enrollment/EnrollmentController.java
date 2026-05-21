package com.liveklass.enrollment;

import com.liveklass.common.ApiResponse;
import com.liveklass.enrollment.dto.EnrollmentRequest;
import com.liveklass.enrollment.dto.EnrollmentResponse;
import com.liveklass.enrollment.dto.WaitlistPositionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "수강 신청", description = "수강 신청, 결제 확정, 취소 API")
@RestController
@RequestMapping("/api")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @Operation(summary = "수강 신청", description = "OPEN 강의에 수강 신청합니다. 자리가 있으면 PENDING, 없으면 409를 반환합니다. 크리에이터 본인은 신청 불가.")
    @PostMapping("/enrollments")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> enroll(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid EnrollmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(enrollmentService.enroll(userId, request)));
    }

    @Operation(summary = "결제 확정", description = "PENDING 상태의 수강 신청을 CONFIRMED로 전환합니다. 본인 신청만 확정 가능.")
    @PostMapping("/enrollments/{id}/confirm")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> confirm(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(enrollmentService.confirm(id, userId)));
    }

    @Operation(summary = "수강 취소", description = "수강 신청을 취소합니다. CONFIRMED는 결제 후 7일 이내만 취소 가능. 취소 시 대기열 1번이 자동으로 PENDING 승격됩니다.")
    @PostMapping("/enrollments/{id}/cancel")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> cancel(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(enrollmentService.cancel(id, userId)));
    }

    @Operation(summary = "내 수강 신청 목록", description = "내가 신청한 수강 목록을 페이지네이션으로 조회합니다.")
    @GetMapping("/enrollments/me")
    public ResponseEntity<ApiResponse<Page<EnrollmentResponse>>> findMyEnrollments(
            @RequestHeader("X-User-Id") Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(enrollmentService.findMyEnrollments(userId, pageable)));
    }

    @Operation(summary = "대기열 등록", description = "정원이 가득 찬 강의의 대기열에 등록합니다. 내 대기 순번을 반환합니다.")
    @PostMapping("/courses/{courseId}/waitlist")
    public ResponseEntity<ApiResponse<WaitlistPositionResponse>> joinWaitlist(
            @PathVariable Long courseId,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(enrollmentService.joinWaitlist(courseId, userId)));
    }

    @Operation(summary = "대기 순번 조회", description = "내 대기 순번을 조회합니다. 대기열에 등록된 경우에만 조회 가능.")
    @GetMapping("/courses/{courseId}/waitlist/me")
    public ResponseEntity<ApiResponse<WaitlistPositionResponse>> getWaitlistPosition(
            @PathVariable Long courseId,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(enrollmentService.getWaitlistPosition(courseId, userId)));
    }

    @Operation(summary = "대기열 이탈", description = "대기열에서 이탈합니다.")
    @DeleteMapping("/courses/{courseId}/waitlist/me")
    public ResponseEntity<ApiResponse<Void>> leaveWaitlist(
            @PathVariable Long courseId,
            @RequestHeader("X-User-Id") Long userId) {
        enrollmentService.leaveWaitlist(courseId, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "수강생 목록 조회", description = "강의별 수강생 목록을 조회합니다. 해당 강의의 크리에이터만 조회 가능.")
    @GetMapping("/courses/{courseId}/enrollments")
    public ResponseEntity<ApiResponse<Page<EnrollmentResponse>>> findByCourse(
            @PathVariable Long courseId,
            @RequestHeader("X-User-Id") Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(enrollmentService.findByCourse(courseId, userId, pageable)));
    }
}

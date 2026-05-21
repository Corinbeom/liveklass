package com.liveklass.course;

import com.liveklass.common.ApiResponse;
import com.liveklass.course.dto.CourseCreateRequest;
import com.liveklass.course.dto.CourseResponse;
import com.liveklass.course.dto.CourseStatusUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "강의", description = "강의 등록, 조회, 상태 변경 API")
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @Operation(summary = "강의 등록", description = "새 강의를 DRAFT 상태로 등록합니다. 등록자가 크리에이터가 됩니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<CourseResponse>> create(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid CourseCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(courseService.create(userId, request)));
    }

    @Operation(summary = "강의 목록 조회", description = "전체 강의 목록을 조회합니다. status 파라미터로 필터링할 수 있습니다. (DRAFT / OPEN / CLOSED)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CourseResponse>>> findAll(
            @RequestParam(required = false) CourseStatus status) {
        return ResponseEntity.ok(ApiResponse.success(courseService.findAll(status)));
    }

    @Operation(summary = "강의 상세 조회", description = "강의 ID로 상세 정보를 조회합니다. 현재 확정 수강 인원(enrolledCount)이 포함됩니다.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(courseService.findById(id)));
    }

    @Operation(summary = "강의 상태 변경", description = "강의 상태를 변경합니다. DRAFT → OPEN → CLOSED 순서로만 전이 가능하며, 크리에이터 본인만 변경할 수 있습니다.")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<CourseResponse>> updateStatus(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid CourseStatusUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(courseService.updateStatus(id, userId, request.status())));
    }
}

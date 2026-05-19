package com.liveklass.course;

import com.liveklass.common.ApiResponse;
import com.liveklass.course.dto.CourseCreateRequest;
import com.liveklass.course.dto.CourseResponse;
import com.liveklass.course.dto.CourseStatusUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CourseResponse>> create(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid CourseCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(courseService.create(userId, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CourseResponse>>> findAll(
            @RequestParam(required = false) CourseStatus status) {
        return ResponseEntity.ok(ApiResponse.success(courseService.findAll(status)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(courseService.findById(id)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<CourseResponse>> updateStatus(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid CourseStatusUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(courseService.updateStatus(id, userId, request.status())));
    }
}

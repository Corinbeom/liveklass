package com.liveclass.common;

public record ApiResponse<T>(T data, ErrorResponse error) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, null);
    }

    public static ApiResponse<Void> failure(ErrorResponse error) {
        return new ApiResponse<>(null, error);
    }

    public record ErrorResponse(String code, String message) {}
}

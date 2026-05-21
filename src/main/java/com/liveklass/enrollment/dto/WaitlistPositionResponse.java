package com.liveklass.enrollment.dto;

import com.liveklass.enrollment.Waitlist;

public record WaitlistPositionResponse(
        Long waitlistId,
        Long courseId,
        Long userId,
        long position
) {
    public static WaitlistPositionResponse of(Waitlist waitlist, long position) {
        return new WaitlistPositionResponse(
                waitlist.getId(),
                waitlist.getCourseId(),
                waitlist.getUserId(),
                position
        );
    }
}

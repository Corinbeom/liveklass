package com.liveclass.course;

public enum CourseStatus {
    DRAFT, OPEN, CLOSED;

    public boolean canTransitionTo(CourseStatus next) {
        return switch (this) {
            case DRAFT -> next == OPEN;
            case OPEN -> next == CLOSED;
            case CLOSED -> false;
        };
    }
}

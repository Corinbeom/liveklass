package com.liveclass.course;

import com.liveclass.common.BusinessException;
import com.liveclass.common.ErrorCode;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long creatorId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CourseStatus status;

    @Column(nullable = false)
    private int enrolledCount;

    protected Course() {}

    public Course(Long creatorId, String title, String description, BigDecimal price,
                  int capacity, LocalDate startDate, LocalDate endDate) {
        this.creatorId = creatorId;
        this.title = title;
        this.description = description;
        this.price = price;
        this.capacity = capacity;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = CourseStatus.DRAFT;
        this.enrolledCount = 0;
    }

    public void transition(CourseStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.status = newStatus;
    }

    public void validateCreator(Long userId) {
        if (!this.creatorId.equals(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    public boolean isFull() {
        return this.enrolledCount >= this.capacity;
    }

    public void increaseEnrolledCount() {
        this.enrolledCount++;
    }

    public void decreaseEnrolledCount() {
        if (this.enrolledCount > 0) {
            this.enrolledCount--;
        }
    }

    public Long getId() { return id; }
    public Long getCreatorId() { return creatorId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public int getCapacity() { return capacity; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public CourseStatus getStatus() { return status; }
    public int getEnrolledCount() { return enrolledCount; }
}

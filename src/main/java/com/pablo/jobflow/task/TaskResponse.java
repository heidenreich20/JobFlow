package com.pablo.jobflow.task;

import java.time.LocalDate;

public record TaskResponse(
    Long id,
    Long projectId,
    String title,
    String description,
    TaskStatus status,
    TaskPriority priority,
    LocalDate dueDate
) {
    public static TaskResponse from(Task task) {
        return new TaskResponse(
            task.getId(),
            task.getProject().getId(),
            task.getTitle(),
            task.getDescription(),
            task.getStatus(),
            task.getPriority(),
            task.getDueDate()
        );
    }
}
package com.pablo.jobflow.task;

import org.springframework.data.jpa.domain.Specification;

public class TaskSpecifications {

    private TaskSpecifications() {
    }

    public static Specification<Task> hasProjectId(Long projectId) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("project").get("id"), projectId);
    }

    public static Specification<Task> hasStatus(TaskStatus status) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("status"), status);
    }

    public static Specification<Task> hasPriority(TaskPriority priority) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("priority"), priority);
    }
}
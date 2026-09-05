package com.pablo.jobflow.project;

public record ProjectResponse(
        Long id,
        String name,
        String description
) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription()
        );
    }
}
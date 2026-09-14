package com.pablo.jobflow.project;

public record ProjectResponse(
        Long id,
        String name,
        String description,
        Long ownerId,
        Long organizationId
) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getOwner() != null ? project.getOwner().getId() : null,
                project.getOrganization() != null ? project.getOrganization().getId() : null
        );
    }
}
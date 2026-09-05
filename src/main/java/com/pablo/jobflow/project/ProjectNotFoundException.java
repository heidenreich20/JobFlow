package com.pablo.jobflow.project;

public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(Long id) {
        super("Project not found with id " + id);
    }
}

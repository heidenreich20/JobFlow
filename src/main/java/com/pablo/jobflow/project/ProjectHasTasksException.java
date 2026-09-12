package com.pablo.jobflow.project;

public class ProjectHasTasksException extends RuntimeException {

    public ProjectHasTasksException(Long projectId) {
        super("Project " + projectId + " has existing tasks and cannot be deleted without force=true");
    }
    
}
package com.pablo.jobflow.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.pablo.jobflow.project.Project;
import com.pablo.jobflow.project.ProjectRequest;
import com.pablo.jobflow.project.ProjectResponse;
import com.pablo.jobflow.project.ProjectService;

import jakarta.validation.Valid;

@RestController
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping("/api/projects")
    public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody ProjectRequest request) {
        Project saved = projectService.createProject(request);
        URI location = URI.create("/api/projects/" + saved.getId());
        return ResponseEntity.created(location).body(ProjectResponse.from(saved));
    }

    @GetMapping("/api/projects")
    public List<ProjectResponse> getProjects() {
        return projectService.getProjects()
                .stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @GetMapping("/api/projects/{id}")
    public ProjectResponse getProject(@PathVariable Long id) {
        Project project = projectService.getProject(id);
        return ProjectResponse.from(project);
    }

    @PutMapping("/api/projects/{id}")
    public ProjectResponse updateProject(
            @PathVariable Long id,
            @Valid @RequestBody ProjectRequest request) {

        Project saved = projectService.updateProject(id, request);
        return ProjectResponse.from(saved);
    }

    @DeleteMapping("/api/projects/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }
}
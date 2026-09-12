package com.pablo.jobflow.controller;

import java.net.URI;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.pablo.jobflow.task.TaskPriority;
import com.pablo.jobflow.task.TaskRequest;
import com.pablo.jobflow.task.TaskResponse;
import com.pablo.jobflow.task.TaskService;
import com.pablo.jobflow.task.TaskStatus;
import com.pablo.jobflow.task.TaskStatusRequest;

import jakarta.validation.Valid;

@RequestMapping("/api")
@RestController
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/projects/{projectId}/tasks")
    public ResponseEntity<TaskResponse> createTask(
            @PathVariable Long projectId,
            @RequestBody @Valid TaskRequest request) {

        TaskResponse saved = taskService.createTask(projectId, request);

        // Generates an absolute URL (e.g., http://localhost/api/tasks/1)
        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/tasks/{id}")
                .buildAndExpand(saved.id())
                .toUri();

        return ResponseEntity.created(location).body(saved);
    }

    @GetMapping("/projects/{projectId}/tasks")
    public Page<TaskResponse> getTasksForProject(
            @PathVariable Long projectId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            Pageable pageable) {

        return taskService.getTasksForProject(projectId, status, priority, pageable);
    }

    @GetMapping("/tasks/{id}")
    public TaskResponse getTaskById(@PathVariable Long id) {
        return taskService.getTaskById(id);
    }

    @PutMapping("/tasks/{id}")
    public TaskResponse updateTask(
            @PathVariable Long id,
            @RequestBody @Valid TaskRequest request) {

        return taskService.updateTask(id, request);
    }

    @PatchMapping("/tasks/{id}/status")
    public TaskResponse updateTaskStatus(
            @PathVariable Long id,
            @RequestBody @Valid TaskStatusRequest request) {

        return taskService.updateTaskStatus(id, request);
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {

        taskService.deleteTask(id);

        return ResponseEntity.noContent().build();
    }
}
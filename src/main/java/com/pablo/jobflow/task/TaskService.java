package com.pablo.jobflow.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.pablo.jobflow.project.Project;
import com.pablo.jobflow.project.ProjectNotFoundException;
import com.pablo.jobflow.project.ProjectRepository;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;

    public TaskService(
            TaskRepository taskRepository,
            ProjectRepository projectRepository) {

        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
    }

    public TaskResponse createTask(Long projectId, TaskRequest request) {

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        Task task = new Task();

        task.setTitle(request.title());
        task.setDescription(request.description());

        if (request.priority() != null) {
            task.setPriority(request.priority());
        }

        if (request.dueDate() != null) {
            task.setDueDate(request.dueDate());
        }

        task.setProject(project);

        Task savedTask = taskRepository.save(task);

        return TaskResponse.from(savedTask);
    }

    public Page<TaskResponse> getTasksForProject(
            Long projectId,
            TaskStatus status,
            TaskPriority priority,
            Pageable pageable) {

        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }

        Specification<Task> spec = Specification.where(TaskSpecifications.hasProjectId(projectId));

        if (status != null) {
            spec = spec.and(TaskSpecifications.hasStatus(status));
        }

        if (priority != null) {
            spec = spec.and(TaskSpecifications.hasPriority(priority));
        }

        return taskRepository.findAll(spec, pageable)
                .map(TaskResponse::from);
    }

    public TaskResponse getTaskById(Long id) {

        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        return TaskResponse.from(task);
    }

    public TaskResponse updateTask(Long id, TaskRequest request) {

        Task existingTask = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        existingTask.setTitle(request.title());
        existingTask.setDescription(request.description());

        if (request.priority() != null) {
            existingTask.setPriority(request.priority());
        }

        if (request.dueDate() != null) {
            existingTask.setDueDate(request.dueDate());
        }

        Task updatedTask = taskRepository.save(existingTask);

        return TaskResponse.from(updatedTask);
    }

    public void deleteTask(Long id) {

        if (!taskRepository.existsById(id)) {
            throw new TaskNotFoundException(id);
        }

        taskRepository.deleteById(id);
    }

    public TaskResponse updateTaskStatus(
            Long id,
            TaskStatusRequest request) {

        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        TaskStatus currentStatus = task.getStatus();
        TaskStatus requestedStatus = request.status();

        if (!isValidStatusTransition(
                currentStatus,
                requestedStatus)) {

            throw new InvalidTaskStatusTransitionException(
                    currentStatus,
                    requestedStatus);
        }

        task.setStatus(requestedStatus);

        Task updatedTask = taskRepository.save(task);

        return TaskResponse.from(updatedTask);
    }

    private boolean isValidStatusTransition(
            TaskStatus currentStatus,
            TaskStatus requestedStatus) {

        return switch (currentStatus) {
            case TODO -> requestedStatus == TaskStatus.IN_PROGRESS;
            case IN_PROGRESS -> requestedStatus == TaskStatus.DONE;
            case DONE -> false;
        };
    }
}
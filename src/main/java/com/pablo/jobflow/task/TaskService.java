package com.pablo.jobflow.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.pablo.jobflow.organization.InsufficientOrganizationPermissionException;
import com.pablo.jobflow.project.Project;
import com.pablo.jobflow.project.ProjectNotFoundException;
import com.pablo.jobflow.project.ProjectRepository;
import com.pablo.jobflow.project.ProjectService;
import com.pablo.jobflow.user.User;
import com.pablo.jobflow.user.UserRepository;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final UserRepository userRepository;

    public TaskService(
            TaskRepository taskRepository,
            ProjectRepository projectRepository,
            ProjectService projectService,
            UserRepository userRepository) {

        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.projectService = projectService;
        this.userRepository = userRepository;
    }

    public TaskResponse createTask(Long projectId, TaskRequest request) {

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        User actingUser = getAuthenticatedUser();
        requireCanEditProject(actingUser, project, projectId);

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

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        User actingUser = getAuthenticatedUser();
        requireCanViewProject(actingUser, project, projectId);

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

        User actingUser = getAuthenticatedUser();
        Project project = loadProjectForTask(task);
        requireCanViewTask(actingUser, project, id);

        return TaskResponse.from(task);
    }

    public TaskResponse updateTask(Long id, TaskRequest request) {

        Task existingTask = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        User actingUser = getAuthenticatedUser();
        Project project = loadProjectForTask(existingTask);
        requireCanEditTask(actingUser, project, id);

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

        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        User actingUser = getAuthenticatedUser();
        Project project = loadProjectForTask(task);
        requireCanDeleteTask(actingUser, project, id);

        taskRepository.deleteById(id);
    }

    public TaskResponse updateTaskStatus(
            Long id,
            TaskStatusRequest request) {

        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        User actingUser = getAuthenticatedUser();
        Project project = loadProjectForTask(task);
        requireCanEditTask(actingUser, project, id);

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

    // --- authorization helpers ---

    /**
     * Re-fetches the task's project through the repository rather than using
     * task.getProject() directly. Task.project is a LAZY association and
     * TaskService isn't @Transactional, so touching the lazy proxy's fields
     * (owner/organization) here would risk LazyInitializationException.
     * task.getProject().getId() itself is always safe (Hibernate resolves the
     * id from the FK without initializing the proxy) - it's only used to look
     * the project up fresh, fully loaded, through ProjectRepository.
     */
    private Project loadProjectForTask(Task task) {
        Long projectId = task.getProject().getId();
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
    }

    private void requireCanViewProject(User user, Project project, Long projectId) {
        if (!projectService.canView(user, project)) {
            throw new ProjectNotFoundException(projectId);
        }
    }

    private void requireCanEditProject(User user, Project project, Long projectId) {
        if (!projectService.canView(user, project)) {
            throw new ProjectNotFoundException(projectId);
        }
        if (!projectService.canEdit(user, project)) {
            throw new InsufficientOrganizationPermissionException(
                    "Insufficient permission to create tasks in this project.");
        }
    }

    private void requireCanViewTask(User user, Project project, Long taskId) {
        if (!projectService.canView(user, project)) {
            throw new TaskNotFoundException(taskId);
        }
    }

    private void requireCanEditTask(User user, Project project, Long taskId) {
        if (!projectService.canView(user, project)) {
            throw new TaskNotFoundException(taskId);
        }
        if (!projectService.canEdit(user, project)) {
            throw new InsufficientOrganizationPermissionException(
                    "Insufficient permission to modify this task.");
        }
    }

    /** Deleting a task requires the same tier as managing its project: OWNER/ADMIN, or the personal owner. */
    private void requireCanDeleteTask(User user, Project project, Long taskId) {
        if (!projectService.canView(user, project)) {
            throw new TaskNotFoundException(taskId);
        }
        if (!projectService.canManage(user, project)) {
            throw new InsufficientOrganizationPermissionException(
                    "Only an OWNER or ADMIN can delete this task.");
        }
    }

    private User getAuthenticatedUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String email = authentication.getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user does not exist"));
    }
}

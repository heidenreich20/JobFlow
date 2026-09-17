package com.pablo.jobflow.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import com.pablo.jobflow.project.Project;
import com.pablo.jobflow.project.ProjectNotFoundException;
import com.pablo.jobflow.project.ProjectRepository;
import com.pablo.jobflow.user.User;
import com.pablo.jobflow.user.UserRepository;

@SpringBootTest
@Transactional
class TaskServiceIntegrationTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    private User actingUser;

    // TaskService now enforces authorization inherited from the project (8.8), so every
    // test needs a real authenticated user, and every project it creates needs an owner
    // that user actually is - otherwise these calls would be denied before reaching the
    // behavior each test is meant to exercise.
    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("task-integration@acme.com");
        user.setPassword("irrelevant-for-this-test");

        actingUser = userRepository.saveAndFlush(user);

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(actingUser.getEmail(), null);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Project newOwnedProject(String name, String description) {
        Project project = new Project();
        project.setName(name);
        project.setDescription(description);
        project.setOwner(actingUser);
        return project;
    }

    @Test
    void createTask_persistsTaskAndAssociatesItWithProject() {
        Project project = newOwnedProject(
                "Service Integration Project", "Testing TaskService with PostgreSQL");

        Project savedProject = projectRepository.saveAndFlush(project);

        TaskResponse response = taskService.createTask(
                savedProject.getId(),
                new TaskRequest(
                        "Service Integration Task",
                        "Created through TaskService",
                        TaskPriority.HIGH,
                        LocalDate.of(2026, 9, 15)));

        assertThat(response.id()).isNotNull();
        assertThat(response.title()).isEqualTo("Service Integration Task");
        assertThat(response.description()).isEqualTo("Created through TaskService");
        assertThat(response.status()).isEqualTo(TaskStatus.TODO);
        assertThat(response.priority()).isEqualTo(TaskPriority.HIGH);
        assertThat(response.dueDate()).isEqualTo(LocalDate.of(2026, 9, 15));

        Task persistedTask = taskRepository.findById(response.id())
                .orElseThrow();

        assertThat(persistedTask.getProject()).isNotNull();
        assertThat(persistedTask.getProject().getId()).isEqualTo(savedProject.getId());
    }

    @Test
    void createTask_whenProjectDoesNotExist_throwsException() {
        Long nonExistentProjectId = 999999L;

        assertThatThrownBy(() -> taskService.createTask(
                nonExistentProjectId,
                new TaskRequest(
                        "Task",
                        "This should not be persisted",
                        null,
                        null)))
                .isInstanceOf(ProjectNotFoundException.class);

        assertThat(taskRepository.findAll())
                .noneMatch(task -> "This should not be persisted".equals(task.getDescription()));
    }

    @Test
    void getTasksForProject_returnsTasksForProject() {
        Project project = newOwnedProject("Task Lookup Project", "Testing service task lookup");
        Project otherProject = newOwnedProject("Other Project", "Should not be returned");

        Project savedProject = projectRepository.saveAndFlush(project);
        Project savedOtherProject = projectRepository.saveAndFlush(otherProject);

        taskService.createTask(
                savedProject.getId(),
                new TaskRequest("Task One", "First task", null, null));
        taskService.createTask(
                savedProject.getId(),
                new TaskRequest("Task Two", "Second task", null, null));
        taskService.createTask(
                savedOtherProject.getId(),
                new TaskRequest("Other Task", "Different project", null, null));

        Pageable pageable = PageRequest.of(0, 10);
        Page<TaskResponse> tasks = taskService.getTasksForProject(
                savedProject.getId(), null, null, pageable);

        assertThat(tasks.getContent()).hasSize(2);
        assertThat(tasks.getContent())
                .extracting(task -> task.title())
                .containsExactlyInAnyOrder("Task One", "Task Two");
        assertThat(tasks.getTotalElements()).isEqualTo(2);
    }

    @Test
    void getTasksForProject_whenProjectDoesNotExist_throwsException() {
        Long nonExistentProjectId = 999999L;
        Pageable pageable = PageRequest.of(0, 10);

        assertThatThrownBy(() -> taskService.getTasksForProject(
                nonExistentProjectId,
                null,
                null,
                pageable))
                .isInstanceOf(ProjectNotFoundException.class);
    }
}

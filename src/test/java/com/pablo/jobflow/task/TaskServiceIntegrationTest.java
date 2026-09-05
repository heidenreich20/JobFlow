package com.pablo.jobflow.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import com.pablo.jobflow.project.Project;
import com.pablo.jobflow.project.ProjectNotFoundException;
import com.pablo.jobflow.project.ProjectRepository;

@SpringBootTest
@Transactional
class TaskServiceIntegrationTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Test
    void createTask_persistsTaskAndAssociatesItWithProject() {
        Project project = new Project();
        project.setName("Service Integration Project");
        project.setDescription("Testing TaskService with PostgreSQL");

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
        Project project = new Project();
        project.setName("Task Lookup Project");
        project.setDescription("Testing service task lookup");

        Project otherProject = new Project();
        otherProject.setName("Other Project");
        otherProject.setDescription("Should not be returned");

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
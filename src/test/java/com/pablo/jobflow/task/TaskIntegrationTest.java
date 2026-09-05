package com.pablo.jobflow.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import com.pablo.jobflow.project.Project;
import com.pablo.jobflow.project.ProjectRepository;

@SpringBootTest
@Transactional
class TaskIntegrationTest {

        @Autowired
        private TaskRepository taskRepository;

        @Autowired
        private ProjectRepository projectRepository;

        @Test
        void createTask_persistsTaskWithProject() {

                Project project = new Project();
                project.setName("Integration Test Project");
                project.setDescription(
                                "Project created during integration testing");

                Project savedProject = projectRepository.saveAndFlush(project);

                Task task = new Task();
                task.setTitle("Integration Test Task");
                task.setDescription("Task persisted in PostgreSQL");
                task.setProject(savedProject);

                Task savedTask = taskRepository.saveAndFlush(task);

                assertThat(savedTask.getId()).isNotNull();
                assertThat(savedTask.getTitle())
                                .isEqualTo("Integration Test Task");
                assertThat(savedTask.getDescription())
                                .isEqualTo("Task persisted in PostgreSQL");
                assertThat(savedTask.getStatus())
                                .isEqualTo(TaskStatus.TODO);
                assertThat(savedTask.getPriority())
                                .isEqualTo(TaskPriority.MEDIUM);
                assertThat(savedTask.getProject())
                                .isNotNull();
                assertThat(savedTask.getProject().getId())
                                .isEqualTo(savedProject.getId());
        }

        @Test
        void findByProjectId_returnsOnlyTasksBelongingToProject() {

                Project firstProject = new Project();
                firstProject.setName("First Project");
                firstProject.setDescription("Project with tasks");

                Project secondProject = new Project();
                secondProject.setName("Second Project");
                secondProject.setDescription("Different project");

                Project savedFirstProject = projectRepository.saveAndFlush(firstProject);

                Project savedSecondProject = projectRepository.saveAndFlush(secondProject);

                Task firstTask = new Task();
                firstTask.setTitle("First Task");
                firstTask.setDescription("First project task");
                firstTask.setProject(savedFirstProject);

                Task secondTask = new Task();
                secondTask.setTitle("Second Task");
                secondTask.setDescription("Second project task");
                secondTask.setProject(savedFirstProject);

                Task unrelatedTask = new Task();
                unrelatedTask.setTitle("Unrelated Task");
                unrelatedTask.setDescription("Belongs to another project");
                unrelatedTask.setProject(savedSecondProject);

                taskRepository.saveAndFlush(firstTask);
                taskRepository.saveAndFlush(secondTask);
                taskRepository.saveAndFlush(unrelatedTask);

                Pageable pageable = PageRequest.of(0, 10);

                Page<Task> tasks = taskRepository.findByProjectId(
                                savedFirstProject.getId(),
                                pageable);

                assertThat(tasks.getContent())
                                .hasSize(2);

                assertThat(tasks.getContent())
                                .extracting(task -> task.getTitle())
                                .containsExactlyInAnyOrder(
                                                "First Task",
                                                "Second Task");

                assertThat(tasks.getContent())
                                .extracting(task -> task.getProject())
                                .allMatch(project -> project.getId().equals(savedFirstProject.getId()));

                assertThat(tasks.getTotalElements())
                                .isEqualTo(2);
        }
}
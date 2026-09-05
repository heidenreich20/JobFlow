package com.pablo.jobflow.task;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.pablo.jobflow.project.Project;
import com.pablo.jobflow.project.ProjectNotFoundException;
import com.pablo.jobflow.project.ProjectRepository;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

        private static final Long PROJECT_ID = 1L;
        private static final Long TASK_ID = 5L;
        private static final Long NOT_FOUND_ID = 999L;
        private static final LocalDate DUE_DATE = LocalDate.of(2026, 9, 15);

        @Mock
        private TaskRepository taskRepository;

        @Mock
        private ProjectRepository projectRepository;

        @InjectMocks
        private TaskService taskService;

        @Captor
        private ArgumentCaptor<Task> taskCaptor;

        private Project project;

        @BeforeEach
        void setUp() {
                project = new Project();
        }

        // -------------------------------------------------------------------------
        // CREATE
        // -------------------------------------------------------------------------

        @Test
        void createTask_whenProjectExists_createsTask() {
                when(projectRepository.findById(PROJECT_ID))
                                .thenReturn(Optional.of(project));

                Task savedTask = buildTask(
                                "Test Task",
                                "A task for testing",
                                TaskStatus.TODO);

                when(taskRepository.save(any(Task.class)))
                                .thenReturn(savedTask);

                TaskResponse response = taskService.createTask(
                                PROJECT_ID,
                                buildRequest("Test Task", "A task for testing"));

                assertAll(
                                () -> assertEquals("Test Task", response.title()),
                                () -> assertEquals("A task for testing", response.description()),
                                () -> assertEquals(TaskStatus.TODO, response.status()));

                verify(projectRepository).findById(PROJECT_ID);
                verify(taskRepository).save(any(Task.class));
        }

        @Test
        void createTask_whenProjectNotFound_throwsException() {
                when(projectRepository.findById(NOT_FOUND_ID))
                                .thenReturn(Optional.empty());

                assertThrows(
                                ProjectNotFoundException.class,
                                () -> taskService.createTask(
                                                NOT_FOUND_ID,
                                                buildRequest(
                                                                "Test Task",
                                                                "A task for testing")));

                verify(taskRepository, never()).save(any(Task.class));
        }

        @Test
        void createTask_assignsProjectToTask() {
                when(projectRepository.findById(PROJECT_ID))
                                .thenReturn(Optional.of(project));

                when(taskRepository.save(any(Task.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                taskService.createTask(
                                PROJECT_ID,
                                buildRequest("Test Task", "A task for testing"));

                verify(taskRepository).save(taskCaptor.capture());

                assertEquals(
                                project,
                                taskCaptor.getValue().getProject());
        }

        @Test
        void createTask_withPriority_savesPriority() {
                when(projectRepository.findById(PROJECT_ID))
                                .thenReturn(Optional.of(project));

                Task savedTask = buildTask(
                                "High Priority Task",
                                "Important task",
                                TaskStatus.TODO);

                savedTask.setPriority(TaskPriority.HIGH);

                when(taskRepository.save(any(Task.class)))
                                .thenReturn(savedTask);

                TaskRequest request = new TaskRequest(
                                "High Priority Task",
                                "Important task",
                                TaskPriority.HIGH,
                                null);

                TaskResponse response = taskService.createTask(PROJECT_ID, request);

                assertEquals(TaskPriority.HIGH, response.priority());
        }

        @Test
        void createTask_withoutPriority_defaultsToMedium() {
                when(projectRepository.findById(PROJECT_ID))
                                .thenReturn(Optional.of(project));

                when(taskRepository.save(any(Task.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                TaskResponse response = taskService.createTask(
                                PROJECT_ID,
                                buildRequest(
                                                "Normal Task",
                                                "No explicit priority"));

                assertEquals(TaskPriority.MEDIUM, response.priority());
        }

        @Test
        void createTask_withDueDate_savesDueDate() {
                when(projectRepository.findById(PROJECT_ID))
                                .thenReturn(Optional.of(project));

                Task savedTask = buildTask(
                                "Deadline Task",
                                "Task with deadline",
                                TaskStatus.TODO);

                savedTask.setDueDate(DUE_DATE);

                when(taskRepository.save(any(Task.class)))
                                .thenReturn(savedTask);

                TaskRequest request = new TaskRequest(
                                "Deadline Task",
                                "Task with deadline",
                                null,
                                DUE_DATE);

                TaskResponse response = taskService.createTask(PROJECT_ID, request);

                assertEquals(DUE_DATE, response.dueDate());
        }

        @Test
        void createTask_withoutDueDate_leavesDueDateNull() {
                when(projectRepository.findById(PROJECT_ID))
                                .thenReturn(Optional.of(project));

                when(taskRepository.save(any(Task.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                TaskResponse response = taskService.createTask(
                                PROJECT_ID,
                                buildRequest(
                                                "No Deadline Task",
                                                "No deadline"));

                assertNull(response.dueDate());
        }

        // -------------------------------------------------------------------------
        // GET TASKS
        // -------------------------------------------------------------------------

        @Test
        void getTasksForProject_whenProjectExists_returnsTasks() {
                when(projectRepository.existsById(PROJECT_ID))
                                .thenReturn(true);

                Task task1 = buildTask(
                                "Task 1",
                                "Description 1",
                                TaskStatus.TODO);

                Task task2 = buildTask(
                                "Task 2",
                                "Description 2",
                                TaskStatus.IN_PROGRESS);

                Pageable pageable = PageRequest.of(0, 10);

                Page<Task> taskPage = new PageImpl<>(
                                List.of(task1, task2),
                                pageable,
                                2);

                when(taskRepository.findAll(
                                ArgumentMatchers.<Specification<Task>>any(),
                                eq(pageable)))
                                .thenReturn(taskPage);

                Page<TaskResponse> response = taskService.getTasksForProject(
                                PROJECT_ID,
                                null,
                                null,
                                pageable);

                assertAll(
                                () -> assertEquals(
                                                2,
                                                response.getContent().size()),

                                () -> assertEquals(
                                                "Task 1",
                                                response.getContent().get(0).title()),

                                () -> assertEquals(
                                                "Task 2",
                                                response.getContent().get(1).title()),

                                () -> assertEquals(
                                                2,
                                                response.getTotalElements()));

                verify(projectRepository).existsById(PROJECT_ID);

                verify(taskRepository).findAll(
                        ArgumentMatchers.<Specification<Task>>any(),
                        eq(pageable));
        }

        @Test
        void getTasksForProject_whenProjectNotFound_throwsException() {
                when(projectRepository.existsById(NOT_FOUND_ID))
                                .thenReturn(false);

                Pageable pageable = PageRequest.of(0, 10);

                assertThrows(
                                ProjectNotFoundException.class,
                                () -> taskService.getTasksForProject(
                                                NOT_FOUND_ID,
                                                null,
                                                null,
                                                pageable));

                verify(taskRepository, never()).findAll(
                                ArgumentMatchers.<Specification<Task>>any(),
                                any(Pageable.class));
        }

        @Test
        void getTaskById_whenExists_returnsTask() {
                Task task = buildTask(
                                "Existing Task",
                                "Already saved",
                                TaskStatus.TODO);

                when(taskRepository.findById(TASK_ID))
                                .thenReturn(Optional.of(task));

                TaskResponse response = taskService.getTaskById(TASK_ID);

                assertAll(
                                () -> assertEquals(
                                                "Existing Task",
                                                response.title()),

                                () -> assertEquals(
                                                "Already saved",
                                                response.description()),

                                () -> assertEquals(
                                                TaskStatus.TODO,
                                                response.status()));

                verify(taskRepository).findById(TASK_ID);
        }

        @Test
        void getTaskById_whenNotFound_throwsException() {
                when(taskRepository.findById(NOT_FOUND_ID))
                                .thenReturn(Optional.empty());

                assertThrows(
                                TaskNotFoundException.class,
                                () -> taskService.getTaskById(NOT_FOUND_ID));
        }

        // -------------------------------------------------------------------------
        // UPDATE
        // -------------------------------------------------------------------------

        @Test
        void updateTask_whenExists_updatesTask() {
                Task existingTask = buildTask(
                                "Old Title",
                                "Old description",
                                TaskStatus.TODO);

                when(taskRepository.findById(TASK_ID))
                                .thenReturn(Optional.of(existingTask));

                when(taskRepository.save(existingTask))
                                .thenReturn(existingTask);

                TaskResponse response = taskService.updateTask(
                                TASK_ID,
                                buildRequest(
                                                "Updated title",
                                                "Updated description"));

                assertAll(
                                () -> assertEquals(
                                                "Updated title",
                                                response.title()),

                                () -> assertEquals(
                                                "Updated description",
                                                response.description()),

                                () -> assertEquals(
                                                TaskStatus.TODO,
                                                response.status()));

                verify(taskRepository).findById(TASK_ID);
                verify(taskRepository).save(existingTask);
        }

        @Test
        void updateTask_whenNotFound_throwsException() {
                when(taskRepository.findById(NOT_FOUND_ID))
                                .thenReturn(Optional.empty());

                assertThrows(
                                TaskNotFoundException.class,
                                () -> taskService.updateTask(
                                                NOT_FOUND_ID,
                                                buildRequest(
                                                                "Updated title",
                                                                "Updated description")));

                verify(taskRepository, never())
                                .save(any(Task.class));
        }

        @Test
        void updateTask_withDueDate_updatesDueDate() {
                Task existingTask = buildTask(
                                "Old Task",
                                "Old description",
                                TaskStatus.TODO);

                existingTask.setDueDate(
                                LocalDate.of(2026, 9, 10));

                when(taskRepository.findById(TASK_ID))
                                .thenReturn(Optional.of(existingTask));

                when(taskRepository.save(existingTask))
                                .thenReturn(existingTask);

                TaskRequest request = new TaskRequest(
                                "Updated Task",
                                "Updated description",
                                null,
                                LocalDate.of(2026, 9, 20));

                TaskResponse response = taskService.updateTask(TASK_ID, request);

                assertEquals(
                                LocalDate.of(2026, 9, 20),
                                response.dueDate());

                verify(taskRepository).save(existingTask);
        }

        // -------------------------------------------------------------------------
        // DELETE
        // -------------------------------------------------------------------------

        @Test
        void deleteTask_whenExists_deletesTask() {
                when(taskRepository.existsById(TASK_ID))
                                .thenReturn(true);

                taskService.deleteTask(TASK_ID);

                verify(taskRepository).existsById(TASK_ID);
                verify(taskRepository).deleteById(TASK_ID);
        }

        @Test
        void deleteTask_whenNotFound_throwsException() {
                when(taskRepository.existsById(NOT_FOUND_ID))
                                .thenReturn(false);

                assertThrows(
                                TaskNotFoundException.class,
                                () -> taskService.deleteTask(NOT_FOUND_ID));

                verify(taskRepository, never())
                                .deleteById(NOT_FOUND_ID);
        }

        // -------------------------------------------------------------------------
        // STATUS TRANSITIONS
        // -------------------------------------------------------------------------

        @Test
        void updateTaskStatus_fromTodoToInProgress_succeeds() {
                Task task = buildTask(
                                "Test Task",
                                null,
                                TaskStatus.TODO);

                when(taskRepository.findById(TASK_ID))
                                .thenReturn(Optional.of(task));

                when(taskRepository.save(task))
                                .thenReturn(task);

                TaskResponse response = taskService.updateTaskStatus(
                                TASK_ID,
                                new TaskStatusRequest(
                                                TaskStatus.IN_PROGRESS));

                assertEquals(
                                TaskStatus.IN_PROGRESS,
                                response.status());

                verify(taskRepository).save(task);
        }

        @Test
        void updateTaskStatus_fromInProgressToDone_succeeds() {
                Task task = buildTask(
                                "Test Task",
                                null,
                                TaskStatus.IN_PROGRESS);

                when(taskRepository.findById(TASK_ID))
                                .thenReturn(Optional.of(task));

                when(taskRepository.save(task))
                                .thenReturn(task);

                TaskResponse response = taskService.updateTaskStatus(
                                TASK_ID,
                                new TaskStatusRequest(
                                                TaskStatus.DONE));

                assertEquals(
                                TaskStatus.DONE,
                                response.status());

                verify(taskRepository).save(task);
        }

        @Test
        void updateTaskStatus_fromTodoToDone_throwsException() {
                assertInvalidTransition(
                                TaskStatus.TODO,
                                TaskStatus.DONE);
        }

        @Test
        void updateTaskStatus_fromInProgressToTodo_throwsException() {
                assertInvalidTransition(
                                TaskStatus.IN_PROGRESS,
                                TaskStatus.TODO);
        }

        @Test
        void updateTaskStatus_fromDoneToTodo_throwsException() {
                assertInvalidTransition(
                                TaskStatus.DONE,
                                TaskStatus.TODO);
        }

        @Test
        void updateTaskStatus_fromDoneToInProgress_throwsException() {
                assertInvalidTransition(
                                TaskStatus.DONE,
                                TaskStatus.IN_PROGRESS);
        }

        // -------------------------------------------------------------------------
        // TEST HELPERS
        // -------------------------------------------------------------------------

        private Task buildTask(
                        String title,
                        String description,
                        TaskStatus status) {

                Task task = new Task();

                task.setTitle(title);
                task.setDescription(description);
                task.setStatus(status);
                task.setProject(project);

                return task;
        }

        private TaskRequest buildRequest(
                        String title,
                        String description) {

                return new TaskRequest(
                                title,
                                description,
                                null,
                                null);
        }

        private void assertInvalidTransition(
                        TaskStatus currentStatus,
                        TaskStatus targetStatus) {

                Task task = buildTask(
                                "Test Task",
                                null,
                                currentStatus);

                when(taskRepository.findById(TASK_ID))
                                .thenReturn(Optional.of(task));

                assertThrows(
                                InvalidTaskStatusTransitionException.class,
                                () -> taskService.updateTaskStatus(
                                                TASK_ID,
                                                new TaskStatusRequest(
                                                                targetStatus)));

                verify(taskRepository, never())
                                .save(any(Task.class));
        }
}

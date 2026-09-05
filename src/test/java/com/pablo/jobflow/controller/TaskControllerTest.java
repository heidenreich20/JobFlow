package com.pablo.jobflow.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.pablo.jobflow.config.SpringDataConfig;
import com.pablo.jobflow.project.ProjectNotFoundException;
import com.pablo.jobflow.task.InvalidTaskStatusTransitionException;
import com.pablo.jobflow.task.TaskNotFoundException;
import com.pablo.jobflow.task.TaskPriority;
import com.pablo.jobflow.task.TaskRequest;
import com.pablo.jobflow.task.TaskResponse;
import com.pablo.jobflow.task.TaskService;
import com.pablo.jobflow.task.TaskStatus;
import com.pablo.jobflow.task.TaskStatusRequest;

@WebMvcTest(TaskController.class)
@Import(SpringDataConfig.class)
class TaskControllerTest {

    // --- API Endpoints ---
    private static final String PROJECT_TASKS_API = "/api/projects/{projectId}/tasks";
    private static final String TASKS_API = "/api/tasks/{taskId}";
    private static final String TASK_STATUS_API = "/api/tasks/{taskId}/status";

    // --- JSON Payloads ---
    private static final String TASK_JSON = """
            {
                "title": "Test Task",
                "description": "A task for testing"
            }
            """;

    private static final String UPDATE_TASK_JSON = """
            {
                "title": "Updated Task",
                "description": "Updated description"
            }
            """;

    private static final String IN_PROGRESS_JSON = """
            {
                "status": "IN_PROGRESS"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

    // ---------------------------------------------------------
    // CREATE
    // ---------------------------------------------------------

    @Test
    void createTask_withValidRequest_returns201() throws Exception {
        TaskResponse task = createMockTask(1L, 1L, "Test Task", "A task for testing", TaskStatus.TODO);

        when(taskService.createTask(eq(1L), any(TaskRequest.class))).thenReturn(task);

        mockMvc.perform(post(PROJECT_TASKS_API, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(TASK_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/tasks/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Test Task"))
                .andExpect(jsonPath("$.description").value("A task for testing"))
                .andExpect(jsonPath("$.status").value("TODO"));

        verify(taskService).createTask(eq(1L), any(TaskRequest.class));
    }

    @Test
    void createTask_whenProjectNotFound_returns404() throws Exception {
        when(taskService.createTask(eq(999L), any(TaskRequest.class)))
                .thenThrow(new ProjectNotFoundException(999L));

        mockMvc.perform(post(PROJECT_TASKS_API, 999L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(TASK_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Project not found with id 999"));
    }

    @Test
    void createTask_withBlankTitle_returnsValidationError() throws Exception {
        String json = """
                {
                    "title": "",
                    "description": "A valid description"
                }
                """;

        mockMvc.perform(post(PROJECT_TASKS_API, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.title").value("Title is required"));
    }

    @Test
    void createTask_withWhitespaceOnlyTitle_returns400() throws Exception {
        String json = """
                {
                    "title": "   ",
                    "description": "A valid description"
                }
                """;

        mockMvc.perform(post(PROJECT_TASKS_API, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTask_withMissingTitle_returns400() throws Exception {
        String json = """
                {
                    "description": "A valid description"
                }
                """;

        mockMvc.perform(post(PROJECT_TASKS_API, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTask_withTitleTooLong_returns400() throws Exception {
        String json = """
                {
                    "title": "%s",
                    "description": "A valid description"
                }
                """.formatted("a".repeat(121));

        mockMvc.perform(post(PROJECT_TASKS_API, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTask_withDescriptionTooLong_returns400() throws Exception {
        String json = """
                {
                    "title": "Valid title",
                    "description": "%s"
                }
                """.formatted("a".repeat(2001));

        mockMvc.perform(post(PROJECT_TASKS_API, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTask_withoutDescription_returns201() throws Exception {
        TaskResponse task = createMockTask(1L, 1L, "Task without description", null, TaskStatus.TODO);

        when(taskService.createTask(eq(1L), any(TaskRequest.class))).thenReturn(task);

        String json = """
                {
                    "title": "Task without description"
                }
                """;

        mockMvc.perform(post(PROJECT_TASKS_API, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Task without description"))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    @Test
    void createTask_withDueDate_returnsDueDate() throws Exception {
        TaskResponse task = new TaskResponse(
                1L, 1L, "Task with due date", "A task with a deadline",
                TaskStatus.TODO, TaskPriority.MEDIUM, LocalDate.of(2026, 9, 15)
        );

        when(taskService.createTask(eq(1L), any(TaskRequest.class))).thenReturn(task);

        String json = """
                {
                    "title": "Task with due date",
                    "description": "A task with a deadline",
                    "dueDate": "2026-09-15"
                }
                """;

        mockMvc.perform(post(PROJECT_TASKS_API, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dueDate").value("2026-09-15"));
    }

    // ---------------------------------------------------------
    // GET
    // ---------------------------------------------------------

    @Test
    void getTasksForProject_returnsTasks() throws Exception {
        TaskResponse task1 = createMockTask(1L, 1L, "Task 1", "Description 1", TaskStatus.TODO);
        TaskResponse task2 = createMockTask(2L, 1L, "Task 2", "Description 2", TaskStatus.IN_PROGRESS);

        Page<TaskResponse> page = new PageImpl<>(List.of(task1, task2), PageRequest.of(0, 10), 2);

        when(taskService.getTasksForProject(eq(1L), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get(PROJECT_TASKS_API, 1L)
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Task 1"))
                .andExpect(jsonPath("$.content[0].description").value("Description 1"))
                .andExpect(jsonPath("$.content[0].status").value("TODO"))
                .andExpect(jsonPath("$.content[1].id").value(2))
                .andExpect(jsonPath("$.content[1].title").value("Task 2"))
                .andExpect(jsonPath("$.content[1].description").value("Description 2"))
                .andExpect(jsonPath("$.content[1].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.number").value(0));

        verify(taskService).getTasksForProject(eq(1L), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void getTasksForProject_whenNoTasks_returnsEmptyPage() throws Exception {
        Page<TaskResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

        when(taskService.getTasksForProject(eq(1L), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get(PROJECT_TASKS_API, 1L)
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(10));

        verify(taskService).getTasksForProject(eq(1L), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void getTasksForProject_whenProjectNotFound_returns404() throws Exception {
        when(taskService.getTasksForProject(eq(999L), isNull(), isNull(), any(Pageable.class)))
                .thenThrow(new ProjectNotFoundException(999L));

        mockMvc.perform(get(PROJECT_TASKS_API, 999L)
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Project not found with id 999"));
    }

    @Test
    void getTaskById_whenExists_returnsTask() throws Exception {
        TaskResponse task = createMockTask(5L, 1L, "Existing Task", "Already saved", TaskStatus.TODO);

        when(taskService.getTaskById(5L)).thenReturn(task);

        mockMvc.perform(get(TASKS_API, 5L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.title").value("Existing Task"))
                .andExpect(jsonPath("$.description").value("Already saved"))
                .andExpect(jsonPath("$.status").value("TODO"));

        verify(taskService).getTaskById(5L);
    }

    @Test
    void getTaskById_whenNotFound_returns404() throws Exception {
        when(taskService.getTaskById(999L)).thenThrow(new TaskNotFoundException(999L));

        mockMvc.perform(get(TASKS_API, 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Task not found with id 999"));
    }

    // ---------------------------------------------------------
    // UPDATE
    // ---------------------------------------------------------

    @Test
    void updateTask_withValidRequest_returns200AndUpdatedTask() throws Exception {
        TaskResponse task = createMockTask(5L, 1L, "Updated Task", "Updated description", TaskStatus.TODO);

        when(taskService.updateTask(eq(5L), any(TaskRequest.class))).thenReturn(task);

        mockMvc.perform(put(TASKS_API, 5L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_TASK_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.title").value("Updated Task"))
                .andExpect(jsonPath("$.description").value("Updated description"))
                .andExpect(jsonPath("$.status").value("TODO"));

        verify(taskService).updateTask(eq(5L), any(TaskRequest.class));
    }

    @Test
    void updateTask_whenNotFound_returns404() throws Exception {
        when(taskService.updateTask(eq(999L), any(TaskRequest.class)))
                .thenThrow(new TaskNotFoundException(999L));

        mockMvc.perform(put(TASKS_API, 999L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_TASK_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Task not found with id 999"));
    }

    // ---------------------------------------------------------
    // DELETE
    // ---------------------------------------------------------

    @Test
    void deleteTask_whenExists_returns204() throws Exception {
        mockMvc.perform(delete(TASKS_API, 5L))
                .andExpect(status().isNoContent());

        verify(taskService).deleteTask(5L);
    }

    @Test
    void deleteTask_whenNotFound_returns404() throws Exception {
        doThrow(new TaskNotFoundException(999L)).when(taskService).deleteTask(999L);

        mockMvc.perform(delete(TASKS_API, 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Task not found with id 999"));
    }

    // ---------------------------------------------------------
    // STATUS
    // ---------------------------------------------------------

    @Test
    void updateTaskStatus_withValidRequest_returns200AndUpdatedTask() throws Exception {
        TaskResponse task = createMockTask(5L, 1L, "Test Task", "A task for testing", TaskStatus.IN_PROGRESS);

        when(taskService.updateTaskStatus(eq(5L), any(TaskStatusRequest.class))).thenReturn(task);

        mockMvc.perform(patch(TASK_STATUS_API, 5L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(IN_PROGRESS_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.title").value("Test Task"))
                .andExpect(jsonPath("$.description").value("A task for testing"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        verify(taskService).updateTaskStatus(eq(5L), any(TaskStatusRequest.class));
    }

    @Test
    void updateTaskStatus_whenNotFound_returns404() throws Exception {
        when(taskService.updateTaskStatus(eq(999L), any(TaskStatusRequest.class)))
                .thenThrow(new TaskNotFoundException(999L));

        mockMvc.perform(patch(TASK_STATUS_API, 999L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(IN_PROGRESS_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Task not found with id 999"));
    }

    @Test
    void updateTaskStatus_withNullStatus_returns400() throws Exception {
        mockMvc.perform(patch(TASK_STATUS_API, 5L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"status\": null }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTaskStatus_withMissingStatus_returns400() throws Exception {
        mockMvc.perform(patch(TASK_STATUS_API, 5L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTaskStatus_withInvalidStatus_returns400() throws Exception {
        mockMvc.perform(patch(TASK_STATUS_API, 5L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"status\": \"INVALID\" }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTaskStatus_whenTransitionIsInvalid_returns400() throws Exception {
        when(taskService.updateTaskStatus(eq(1L), any(TaskStatusRequest.class)))
                .thenThrow(new InvalidTaskStatusTransitionException(TaskStatus.TODO, TaskStatus.DONE));

        mockMvc.perform(patch(TASK_STATUS_API, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"status\": \"DONE\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Cannot change task status from TODO to DONE"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    // ---------------------------------------------------------
    // TEST HELPERS
    // ---------------------------------------------------------

    private TaskResponse createMockTask(Long id, Long projectId, String title, String description, TaskStatus status) {
        return new TaskResponse(
                id,
                projectId,
                title,
                description,
                status,
                TaskPriority.MEDIUM,
                null
        );
    }
}
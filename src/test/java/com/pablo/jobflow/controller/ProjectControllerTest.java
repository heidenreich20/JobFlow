package com.pablo.jobflow.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.pablo.jobflow.project.Project;
import com.pablo.jobflow.project.ProjectNotFoundException;
import com.pablo.jobflow.project.ProjectRequest;
import com.pablo.jobflow.project.ProjectService;

@WebMvcTest(ProjectController.class)
class ProjectControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private ProjectService projectService;

        @Test
        void createProject_withValidRequest_returns201AndLocation() throws Exception {
                Project savedProject = new Project();
                savedProject.setName("Test Project");
                savedProject.setDescription("A project for testing");
                ReflectionTestUtils.setField(savedProject, "id", 1L);

                when(projectService.createProject(any(ProjectRequest.class)))
                                .thenReturn(savedProject);

                mockMvc.perform(post("/api/projects")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Test Project\",\"description\":\"A project for testing\"}"))
                                .andExpect(status().isCreated())
                                .andExpect(header().string("Location", "/api/projects/1"))
                                .andExpect(jsonPath("$.id").value(1))
                                .andExpect(jsonPath("$.name").value("Test Project"))
                                .andExpect(jsonPath("$.description").value("A project for testing"));
        }

        @Test
        void getProject_whenExists_returnsProject() throws Exception {
                Project project = new Project();
                project.setName("Existing Project");
                project.setDescription("Already saved");
                ReflectionTestUtils.setField(project, "id", 5L);

                when(projectService.getProject(5L)).thenReturn(project);

                mockMvc.perform(get("/api/projects/5"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(5))
                                .andExpect(jsonPath("$.name").value("Existing Project"))
                                .andExpect(jsonPath("$.description").value("Already saved"));
        }

        @Test
        void getProject_whenNotFound_returns404() throws Exception {
                when(projectService.getProject(999L))
                                .thenThrow(new ProjectNotFoundException(999L));

                mockMvc.perform(get("/api/projects/999"))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.status").value(404))
                                .andExpect(jsonPath("$.message").value("Project not found with id 999"));
        }

        @Test
        void getAllProjects_returnsAllProjects() throws Exception {
                List<Project> projects = new ArrayList<>();
                Project project1 = new Project();
                Project project2 = new Project();
                project1.setName("Project 1");
                project1.setDescription("Description 1");
                ReflectionTestUtils.setField(project1, "id", 1L);
                project2.setName("Project 2");
                project2.setDescription("Description 2");
                ReflectionTestUtils.setField(project2, "id", 2L);
                projects.add(project1);
                projects.add(project2);
                when(projectService.getProjects()).thenReturn(projects);

                mockMvc.perform(get("/api/projects"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").value(1))
                                .andExpect(jsonPath("$[0].name").value("Project 1"))
                                .andExpect(jsonPath("$[0].description").value("Description 1"))
                                .andExpect(jsonPath("$[1].id").value(2))
                                .andExpect(jsonPath("$[1].name").value("Project 2"))
                                .andExpect(jsonPath("$[1].description").value("Description 2"));
        }

        @Test
        void updateProject_withValidRequest_returns200AndUpdatedProject() throws Exception {
                Project updatedProject = new Project();
                updatedProject.setName("Updated Project");
                updatedProject.setDescription("Updated description");
                ReflectionTestUtils.setField(updatedProject, "id", 5L);

                when(projectService.updateProject(eq(5L), any(ProjectRequest.class)))
                                .thenReturn(updatedProject);

                mockMvc.perform(put("/api/projects/5")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Updated Project\",\"description\":\"Updated description\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(5))
                                .andExpect(jsonPath("$.name").value("Updated Project"))
                                .andExpect(jsonPath("$.description").value("Updated description"));
        }

        @Test
        void deleteProject_whenExists_returns204() throws Exception {
                doNothing().when(projectService).deleteProject(5L);

                mockMvc.perform(delete("/api/projects/5"))
                                .andExpect(status().isNoContent());
        }

        @Test
        void deleteProject_whenNotFound_returns404() throws Exception {
                doThrow(new ProjectNotFoundException(999L))
                                .when(projectService)
                                .deleteProject(999L);

                mockMvc.perform(delete("/api/projects/999"))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.status").value(404))
                                .andExpect(jsonPath("$.message").value("Project not found with id 999"));
        }

        @Test
        void createProject_withBlankName_returns400() throws Exception {
                mockMvc.perform(post("/api/projects")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                    "name": "",
                                                    "description": "A valid description"
                                                }
                                                """))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void createProject_withNameTooLong_returns400() throws Exception {
                String longName = "a".repeat(121);

                mockMvc.perform(post("/api/projects")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                    "name": "%s",
                                                    "description": "A valid description"
                                                }
                                                """.formatted(longName)))
                                .andExpect(status().isBadRequest());
        }
}
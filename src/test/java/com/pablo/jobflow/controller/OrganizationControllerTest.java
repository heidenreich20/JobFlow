package com.pablo.jobflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablo.jobflow.organization.DuplicateOrganizationNameException;
import com.pablo.jobflow.organization.OrganizationNotFoundException;
import com.pablo.jobflow.organization.OrganizationRequest;
import com.pablo.jobflow.organization.OrganizationResponse;
import com.pablo.jobflow.organization.OrganizationService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrganizationController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrganizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrganizationService organizationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // --- POST Tests ---

    @Test
    void createOrganization_ValidRequest_Returns201() throws Exception {
        OrganizationRequest request = new OrganizationRequest("Acme Corp");
        OrganizationResponse response = new OrganizationResponse(
                1L,
                "Acme Corp",
                LocalDateTime.now()
        );

        when(organizationService.createOrganization(any(OrganizationRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Acme Corp"));
    }

    @Test
    void createOrganization_DuplicateName_Returns409() throws Exception {
        OrganizationRequest request = new OrganizationRequest("Acme Corp");

        when(organizationService.createOrganization(any(OrganizationRequest.class)))
                .thenThrow(new DuplicateOrganizationNameException("Acme Corp"));

        mockMvc.perform(post("/api/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void createOrganization_BlankName_Returns400() throws Exception {
        OrganizationRequest request = new OrganizationRequest("");

        mockMvc.perform(post("/api/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(organizationService, never())
                .createOrganization(any(OrganizationRequest.class));
    }

    @Test
    void createOrganization_WhitespaceName_Returns400() throws Exception {
        OrganizationRequest request = new OrganizationRequest("   ");

        mockMvc.perform(post("/api/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(organizationService, never())
                .createOrganization(any(OrganizationRequest.class));
    }

    @Test
    void createOrganization_NameTooLong_Returns400() throws Exception {
        OrganizationRequest request = new OrganizationRequest("A".repeat(256));

        mockMvc.perform(post("/api/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(organizationService, never())
                .createOrganization(any(OrganizationRequest.class));
    }

    // --- GET Tests ---

    @Test
    void getOrganizationById_Existing_Returns200() throws Exception {
        OrganizationResponse response = new OrganizationResponse(
                1L,
                "Acme Corp",
                LocalDateTime.now()
        );

        when(organizationService.getOrganizationById(1L))
                .thenReturn(response);

        mockMvc.perform(get("/api/organizations/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Acme Corp"));
    }

    @Test
    void getOrganizationById_Missing_Returns404() throws Exception {
        when(organizationService.getOrganizationById(99L))
                .thenThrow(new OrganizationNotFoundException(99L));

        mockMvc.perform(get("/api/organizations/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllOrganizations_Returns200() throws Exception {
        List<OrganizationResponse> responses = List.of(
                new OrganizationResponse(
                        1L,
                        "Org 1",
                        LocalDateTime.now()
                ),
                new OrganizationResponse(
                        2L,
                        "Org 2",
                        LocalDateTime.now()
                )
        );

        when(organizationService.getAllOrganizations())
                .thenReturn(responses);

        mockMvc.perform(get("/api/organizations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Org 1"))
                .andExpect(jsonPath("$[1].name").value("Org 2"));
    }

    // --- PUT Tests ---

    @Test
    void updateOrganization_Valid_Returns200() throws Exception {
        OrganizationRequest request = new OrganizationRequest("New Acme");
        OrganizationResponse response = new OrganizationResponse(
                1L,
                "New Acme",
                LocalDateTime.now()
        );

        when(organizationService.updateOrganization(
                eq(1L),
                any(OrganizationRequest.class)
        )).thenReturn(response);

        mockMvc.perform(put("/api/organizations/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("New Acme"));
    }

    @Test
    void updateOrganization_DuplicateName_Returns409() throws Exception {
        OrganizationRequest request = new OrganizationRequest("Existing Name");

        when(organizationService.updateOrganization(
                eq(1L),
                any(OrganizationRequest.class)
        )).thenThrow(new DuplicateOrganizationNameException("Existing Name"));

        mockMvc.perform(put("/api/organizations/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void updateOrganization_Missing_Returns404() throws Exception {
        OrganizationRequest request = new OrganizationRequest("New Name");

        when(organizationService.updateOrganization(
                eq(99L),
                any(OrganizationRequest.class)
        )).thenThrow(new OrganizationNotFoundException(99L));

        mockMvc.perform(put("/api/organizations/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateOrganization_BlankName_Returns400() throws Exception {
        OrganizationRequest request = new OrganizationRequest("");

        mockMvc.perform(put("/api/organizations/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(organizationService, never())
                .updateOrganization(eq(1L), any(OrganizationRequest.class));
    }

    // --- DELETE Tests ---

    @Test
    void deleteOrganization_Existing_Returns204() throws Exception {
        mockMvc.perform(delete("/api/organizations/1"))
                .andExpect(status().isNoContent());

        verify(organizationService).deleteOrganization(1L);
    }

    @Test
    void deleteOrganization_Missing_Returns404() throws Exception {
        doThrow(new OrganizationNotFoundException(99L))
                .when(organizationService)
                .deleteOrganization(99L);

        mockMvc.perform(delete("/api/organizations/99"))
                .andExpect(status().isNotFound());
    }
}
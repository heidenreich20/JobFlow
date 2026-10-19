package com.pablo.jobflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablo.jobflow.organization.AddMemberRequest;
import com.pablo.jobflow.organization.ChangeRoleRequest;
import com.pablo.jobflow.organization.DuplicateMembershipException;
import com.pablo.jobflow.organization.InsufficientOrganizationPermissionException;
import com.pablo.jobflow.organization.LastOwnerException;
import com.pablo.jobflow.organization.MembershipNotFoundException;
import com.pablo.jobflow.organization.OrganizationMembershipResponse;
import com.pablo.jobflow.organization.OrganizationMembershipService;
import com.pablo.jobflow.organization.OrganizationNotFoundException;
import com.pablo.jobflow.organization.OrganizationRole;
import com.pablo.jobflow.user.UserNotFoundException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrganizationMembershipController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrganizationMembershipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrganizationMembershipService membershipService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OrganizationMembershipResponse sampleResponse(OrganizationRole role) {
        return new OrganizationMembershipResponse(
                1L, 2L, "member@acme.com", role, LocalDateTime.now());
    }

    // --- POST /api/organizations/{organizationId}/members (addMember) ---

    @Test
    void addMember_ValidRequest_Returns201() throws Exception {
        AddMemberRequest request = new AddMemberRequest("member@acme.com", OrganizationRole.MEMBER);

        when(membershipService.addMember(eq(1L), any(AddMemberRequest.class)))
                .thenReturn(sampleResponse(OrganizationRole.MEMBER));

        mockMvc.perform(post("/api/organizations/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("member@acme.com"))
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    @Test
    void addMember_InvalidEmail_Returns400() throws Exception {
        AddMemberRequest request = new AddMemberRequest("not-an-email", OrganizationRole.MEMBER);

        mockMvc.perform(post("/api/organizations/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(membershipService, never()).addMember(any(), any());
    }

    @Test
    void addMember_MissingRole_Returns400() throws Exception {
        String bodyWithoutRole = "{\"email\":\"member@acme.com\"}";

        mockMvc.perform(post("/api/organizations/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithoutRole))
                .andExpect(status().isBadRequest());

        verify(membershipService, never()).addMember(any(), any());
    }

    @Test
    void addMember_OrganizationNotFound_Returns404() throws Exception {
        AddMemberRequest request = new AddMemberRequest("member@acme.com", OrganizationRole.MEMBER);

        when(membershipService.addMember(eq(99L), any(AddMemberRequest.class)))
                .thenThrow(new OrganizationNotFoundException(99L));

        mockMvc.perform(post("/api/organizations/99/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void addMember_UserNotFound_Returns404() throws Exception {
        AddMemberRequest request = new AddMemberRequest("ghost@acme.com", OrganizationRole.MEMBER);

        when(membershipService.addMember(eq(1L), any(AddMemberRequest.class)))
                .thenThrow(new UserNotFoundException("ghost@acme.com"));

        mockMvc.perform(post("/api/organizations/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void addMember_InsufficientPermission_Returns403() throws Exception {
        AddMemberRequest request = new AddMemberRequest("member@acme.com", OrganizationRole.MEMBER);

        when(membershipService.addMember(eq(1L), any(AddMemberRequest.class)))
                .thenThrow(new InsufficientOrganizationPermissionException(
                        "Only an OWNER or ADMIN can add members."));

        mockMvc.perform(post("/api/organizations/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void addMember_DuplicateMembership_Returns409() throws Exception {
        AddMemberRequest request = new AddMemberRequest("member@acme.com", OrganizationRole.MEMBER);

        when(membershipService.addMember(eq(1L), any(AddMemberRequest.class)))
                .thenThrow(new DuplicateMembershipException("member@acme.com", "Acme Corp"));

        mockMvc.perform(post("/api/organizations/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // --- PATCH /api/organizations/{organizationId}/members/{userId} (changeRole) ---

    @Test
    void changeRole_ValidRequest_Returns200() throws Exception {
        ChangeRoleRequest request = new ChangeRoleRequest(OrganizationRole.ADMIN);

        when(membershipService.changeRole(eq(1L), eq(2L), any(ChangeRoleRequest.class)))
                .thenReturn(sampleResponse(OrganizationRole.ADMIN));

        mockMvc.perform(patch("/api/organizations/1/members/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void changeRole_MissingRole_Returns400() throws Exception {
        mockMvc.perform(patch("/api/organizations/1/members/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(membershipService, never()).changeRole(any(), any(), any());
    }

    @Test
    void changeRole_OrganizationNotFound_Returns404() throws Exception {
        ChangeRoleRequest request = new ChangeRoleRequest(OrganizationRole.ADMIN);

        when(membershipService.changeRole(eq(99L), eq(2L), any(ChangeRoleRequest.class)))
                .thenThrow(new OrganizationNotFoundException(99L));

        mockMvc.perform(patch("/api/organizations/99/members/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void changeRole_MembershipNotFound_Returns404() throws Exception {
        ChangeRoleRequest request = new ChangeRoleRequest(OrganizationRole.ADMIN);

        when(membershipService.changeRole(eq(1L), eq(99L), any(ChangeRoleRequest.class)))
                .thenThrow(new MembershipNotFoundException(99L, 1L));

        mockMvc.perform(patch("/api/organizations/1/members/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void changeRole_InsufficientPermission_Returns403() throws Exception {
        ChangeRoleRequest request = new ChangeRoleRequest(OrganizationRole.OWNER);

        when(membershipService.changeRole(eq(1L), eq(2L), any(ChangeRoleRequest.class)))
                .thenThrow(new InsufficientOrganizationPermissionException(
                        "Only an OWNER can promote a member to OWNER."));

        mockMvc.perform(patch("/api/organizations/1/members/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void changeRole_LastOwner_Returns409() throws Exception {
        ChangeRoleRequest request = new ChangeRoleRequest(OrganizationRole.ADMIN);

        when(membershipService.changeRole(eq(1L), eq(2L), any(ChangeRoleRequest.class)))
                .thenThrow(new LastOwnerException(
                        "Cannot change role: organization must have at least one OWNER."));

        mockMvc.perform(patch("/api/organizations/1/members/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // --- DELETE /api/organizations/{organizationId}/members/{userId} (removeMember) ---

    @Test
    void removeMember_Existing_Returns204() throws Exception {
        mockMvc.perform(delete("/api/organizations/1/members/2"))
                .andExpect(status().isNoContent());

        verify(membershipService).removeMember(1L, 2L);
    }

    @Test
    void removeMember_MembershipNotFound_Returns404() throws Exception {
        doThrow(new MembershipNotFoundException(99L, 1L))
                .when(membershipService).removeMember(1L, 99L);

        mockMvc.perform(delete("/api/organizations/1/members/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeMember_InsufficientPermission_Returns403() throws Exception {
        doThrow(new InsufficientOrganizationPermissionException(
                "Only an OWNER or ADMIN can remove members."))
                .when(membershipService).removeMember(1L, 2L);

        mockMvc.perform(delete("/api/organizations/1/members/2"))
                .andExpect(status().isForbidden());
    }

    @Test
    void removeMember_LastOwner_Returns409() throws Exception {
        doThrow(new LastOwnerException(
                "Cannot remove: organization must have at least one OWNER."))
                .when(membershipService).removeMember(1L, 2L);

        mockMvc.perform(delete("/api/organizations/1/members/2"))
                .andExpect(status().isConflict());
    }
}

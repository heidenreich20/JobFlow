package com.pablo.jobflow.project;

import com.pablo.jobflow.organization.InsufficientOrganizationPermissionException;
import com.pablo.jobflow.organization.Organization;
import com.pablo.jobflow.organization.OrganizationMembershipRepository;
import com.pablo.jobflow.organization.OrganizationNotFoundException;
import com.pablo.jobflow.organization.OrganizationRepository;
import com.pablo.jobflow.task.TaskRepository;
import com.pablo.jobflow.user.User;
import com.pablo.jobflow.user.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProjectService projectService;

    private User actingUser;
    private Organization organization;

    @BeforeEach
    void setUp() {
        actingUser = new User();
        actingUser.setEmail("actor@acme.com");

        organization = new Organization("Acme Corp");
        organization.setId(1L);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAsActor() {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken("actor@acme.com", null);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    // --- createProject tests ---

    @Test
    void createProject_NoOrganization_CreatesPersonalProjectOwnedByActingUser() {
        ProjectRequest request = new ProjectRequest("Personal Project", "desc", null);

        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        authenticateAsActor();

        Project saved = projectService.createProject(request);

        assertEquals(actingUser, saved.getOwner());
        assertNull(saved.getOrganization());
    }

    @Test
    void createProject_WithOrganization_ActorIsMember_CreatesOrganizationProject() {
        ProjectRequest request = new ProjectRequest("Org Project", "desc", 1L);

        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        when(membershipRepository.existsByUserAndOrganization(actingUser, organization)).thenReturn(true);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        authenticateAsActor();

        Project saved = projectService.createProject(request);

        assertEquals(organization, saved.getOrganization());
        assertNull(saved.getOwner());
    }

    @Test
    void createProject_WithOrganization_ActorNotMember_ThrowsException() {
        ProjectRequest request = new ProjectRequest("Org Project", "desc", 1L);

        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        when(membershipRepository.existsByUserAndOrganization(actingUser, organization)).thenReturn(false);

        authenticateAsActor();

        assertThrows(InsufficientOrganizationPermissionException.class,
                () -> projectService.createProject(request));

        verify(projectRepository, never()).save(any());
    }

    @Test
    void createProject_WithOrganization_OrganizationMissing_ThrowsException() {
        ProjectRequest request = new ProjectRequest("Org Project", "desc", 99L);

        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        when(organizationRepository.findById(99L)).thenReturn(Optional.empty());

        authenticateAsActor();

        assertThrows(OrganizationNotFoundException.class,
                () -> projectService.createProject(request));

        verify(projectRepository, never()).save(any());
    }

    // --- updateProject tests ---

    @Test
    void updateProject_PersonalProject_NoOrganizationInRequest_KeepsExistingOwner() {
        User existingOwner = new User();
        existingOwner.setEmail("owner@acme.com");

        Project existingProject = new Project();
        existingProject.setOwner(existingOwner);

        ProjectRequest request = new ProjectRequest("Updated Name", "Updated desc", null);

        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        authenticateAsActor();

        Project updated = projectService.updateProject(5L, request);

        assertEquals(existingOwner, updated.getOwner());
        assertNull(updated.getOrganization());
        assertEquals("Updated Name", updated.getName());
    }

    @Test
    void updateProject_PersonalToOrganization_ActorIsMember_MovesToOrganization() {
        Project existingProject = new Project();
        existingProject.setOwner(actingUser);

        ProjectRequest request = new ProjectRequest("Name", "desc", 1L);

        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        when(membershipRepository.existsByUserAndOrganization(actingUser, organization)).thenReturn(true);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        authenticateAsActor();

        Project updated = projectService.updateProject(5L, request);

        assertEquals(organization, updated.getOrganization());
        assertNull(updated.getOwner());
    }

    @Test
    void updateProject_PersonalToOrganization_ActorNotMember_ThrowsException() {
        Project existingProject = new Project();
        existingProject.setOwner(actingUser);

        ProjectRequest request = new ProjectRequest("Name", "desc", 1L);

        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        when(membershipRepository.existsByUserAndOrganization(actingUser, organization)).thenReturn(false);

        authenticateAsActor();

        assertThrows(InsufficientOrganizationPermissionException.class,
                () -> projectService.updateProject(5L, request));

        verify(projectRepository, never()).save(any());
    }

    @Test
    void updateProject_OrganizationToPersonal_SetsOwnerToActingUser() {
        Project existingProject = new Project();
        existingProject.setOrganization(organization);

        ProjectRequest request = new ProjectRequest("Name", "desc", null);

        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        when(membershipRepository.existsByUserAndOrganization(actingUser, organization)).thenReturn(true);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        authenticateAsActor();

        Project updated = projectService.updateProject(5L, request);

        assertEquals(actingUser, updated.getOwner());
        assertNull(updated.getOrganization());
    }

    @Test
    void updateProject_OrganizationProject_ActorNotMemberOfCurrentOrg_ThrowsException() {
        Project existingProject = new Project();
        existingProject.setOrganization(organization);

        ProjectRequest request = new ProjectRequest("Name", "desc", null);

        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        when(membershipRepository.existsByUserAndOrganization(actingUser, organization)).thenReturn(false);

        authenticateAsActor();

        assertThrows(InsufficientOrganizationPermissionException.class,
                () -> projectService.updateProject(5L, request));

        verify(projectRepository, never()).save(any());
    }

    @Test
    void updateProject_OrganizationToDifferentOrganization_ActorMemberOfBoth_Succeeds() {
        Organization otherOrg = new Organization("Other Org");
        otherOrg.setId(2L);

        Project existingProject = new Project();
        existingProject.setOrganization(organization);

        ProjectRequest request = new ProjectRequest("Name", "desc", 2L);

        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        when(membershipRepository.existsByUserAndOrganization(actingUser, organization)).thenReturn(true);
        when(organizationRepository.findById(2L)).thenReturn(Optional.of(otherOrg));
        when(membershipRepository.existsByUserAndOrganization(actingUser, otherOrg)).thenReturn(true);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        authenticateAsActor();

        Project updated = projectService.updateProject(5L, request);

        assertEquals(otherOrg, updated.getOrganization());
    }

    @Test
    void updateProject_OrganizationToDifferentOrganization_ActorNotMemberOfTarget_ThrowsException() {
        Organization otherOrg = new Organization("Other Org");
        otherOrg.setId(2L);

        Project existingProject = new Project();
        existingProject.setOrganization(organization);

        ProjectRequest request = new ProjectRequest("Name", "desc", 2L);

        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        when(membershipRepository.existsByUserAndOrganization(actingUser, organization)).thenReturn(true);
        when(organizationRepository.findById(2L)).thenReturn(Optional.of(otherOrg));
        when(membershipRepository.existsByUserAndOrganization(actingUser, otherOrg)).thenReturn(false);

        authenticateAsActor();

        assertThrows(InsufficientOrganizationPermissionException.class,
                () -> projectService.updateProject(5L, request));

        verify(projectRepository, never()).save(any());
    }

    @Test
    void updateProject_TargetOrganizationMissing_ThrowsException() {
        Project existingProject = new Project();
        existingProject.setOwner(actingUser);

        ProjectRequest request = new ProjectRequest("Name", "desc", 99L);

        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        when(organizationRepository.findById(99L)).thenReturn(Optional.empty());

        authenticateAsActor();

        assertThrows(OrganizationNotFoundException.class,
                () -> projectService.updateProject(5L, request));

        verify(projectRepository, never()).save(any());
    }

    @Test
    void updateProject_ProjectMissing_ThrowsException() {
        ProjectRequest request = new ProjectRequest("Name", "desc", null);

        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ProjectNotFoundException.class,
                () -> projectService.updateProject(99L, request));

        verify(projectRepository, never()).save(any());
    }
}
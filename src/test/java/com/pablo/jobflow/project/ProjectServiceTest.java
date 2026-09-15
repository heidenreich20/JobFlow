package com.pablo.jobflow.project;

import com.pablo.jobflow.organization.InsufficientOrganizationPermissionException;
import com.pablo.jobflow.organization.Organization;
import com.pablo.jobflow.organization.OrganizationMembership;
import com.pablo.jobflow.organization.OrganizationMembershipRepository;
import com.pablo.jobflow.organization.OrganizationNotFoundException;
import com.pablo.jobflow.organization.OrganizationRepository;
import com.pablo.jobflow.organization.OrganizationRole;
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

import java.util.List;
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

    private void givenRole(User user, Organization org, OrganizationRole role) {
        when(membershipRepository.findByUserAndOrganization(user, org))
                .thenReturn(Optional.of(new OrganizationMembership(user, org, role)));
    }

    private void givenNotMember(User user, Organization org) {
        when(membershipRepository.findByUserAndOrganization(user, org))
                .thenReturn(Optional.empty());
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
    void createProject_WithOrganization_ActorIsMember_Succeeds() {
        ProjectRequest request = new ProjectRequest("Org Project", "desc", 1L);

        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        givenRole(actingUser, organization, OrganizationRole.MEMBER);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        authenticateAsActor();

        Project saved = projectService.createProject(request);

        assertEquals(organization, saved.getOrganization());
        assertNull(saved.getOwner());
    }

    @Test
    void createProject_WithOrganization_ActorIsViewer_ThrowsException() {
        ProjectRequest request = new ProjectRequest("Org Project", "desc", 1L);

        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        givenRole(actingUser, organization, OrganizationRole.VIEWER);

        authenticateAsActor();

        assertThrows(InsufficientOrganizationPermissionException.class,
                () -> projectService.createProject(request));

        verify(projectRepository, never()).save(any());
    }

    @Test
    void createProject_WithOrganization_ActorNotMember_ThrowsException() {
        ProjectRequest request = new ProjectRequest("Org Project", "desc", 1L);

        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        givenNotMember(actingUser, organization);

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

    // --- getProject / getProjects tests ---

    @Test
    void getProject_PersonalProject_Owner_ReturnsProject() {
        Project existingProject = new Project();
        existingProject.setOwner(actingUser);
        existingProject.setName("My Project");

        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));

        authenticateAsActor();

        Project result = projectService.getProject(5L);

        assertEquals("My Project", result.getName());
    }

    @Test
    void getProject_PersonalProject_NonOwner_ThrowsNotFound() {
        User realOwner = new User();
        realOwner.setEmail("owner@acme.com");

        Project existingProject = new Project();
        existingProject.setOwner(realOwner);

        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));

        authenticateAsActor();

        assertThrows(ProjectNotFoundException.class,
                () -> projectService.getProject(5L));
    }

    @Test
    void getProject_OrgProject_Viewer_ReturnsProject() {
        Project existingProject = new Project();
        existingProject.setOrganization(organization);
        existingProject.setName("Org Project");

        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        givenRole(actingUser, organization, OrganizationRole.VIEWER);

        authenticateAsActor();

        Project result = projectService.getProject(5L);

        assertEquals("Org Project", result.getName());
    }

    @Test
    void getProject_OrgProject_NonMember_ThrowsNotFound() {
        Project existingProject = new Project();
        existingProject.setOrganization(organization);

        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        givenNotMember(actingUser, organization);

        authenticateAsActor();

        assertThrows(ProjectNotFoundException.class,
                () -> projectService.getProject(5L));
    }

    @Test
    void getProject_Missing_ThrowsNotFound() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ProjectNotFoundException.class,
                () -> projectService.getProject(99L));
    }

    @Test
    void getProjects_ReturnsOnlyVisibleProjects() {
        Project myPersonalProject = new Project();
        myPersonalProject.setOwner(actingUser);
        myPersonalProject.setName("Mine");

        User otherUser = new User();
        otherUser.setEmail("other@acme.com");
        Project othersPersonalProject = new Project();
        othersPersonalProject.setOwner(otherUser);
        othersPersonalProject.setName("Not Mine");

        Project myOrgProject = new Project();
        myOrgProject.setOrganization(organization);
        myOrgProject.setName("Org Project I Can See");

        Organization otherOrg = new Organization("Other Org");
        otherOrg.setId(2L);
        Project otherOrgProject = new Project();
        otherOrgProject.setOrganization(otherOrg);
        otherOrgProject.setName("Org Project I Cannot See");

        when(projectRepository.findAll()).thenReturn(List.of(
                myPersonalProject, othersPersonalProject, myOrgProject, otherOrgProject));

        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        givenRole(actingUser, organization, OrganizationRole.MEMBER);
        givenNotMember(actingUser, otherOrg);

        authenticateAsActor();

        List<Project> visible = projectService.getProjects();

        assertEquals(2, visible.size());
        assertTrue(visible.contains(myPersonalProject));
        assertTrue(visible.contains(myOrgProject));
    }

    // --- updateProject tests: field edits only ---

    @Test
    void updateProject_PersonalProject_Owner_UpdatesFields() {
        Project existingProject = new Project();
        existingProject.setOwner(actingUser);

        ProjectRequest request = new ProjectRequest("Updated Name", "Updated desc", null);

        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        authenticateAsActor();

        Project updated = projectService.updateProject(5L, request);

        assertEquals(actingUser, updated.getOwner());
        assertNull(updated.getOrganization());
        assertEquals("Updated Name", updated.getName());
    }

    @Test
    void updateProject_PersonalProject_NonOwner_ThrowsNotFound() {
        User realOwner = new User();
        realOwner.setEmail("owner@acme.com");

        Project existingProject = new Project();
        existingProject.setOwner(realOwner);

        ProjectRequest request = new ProjectRequest("Updated Name", "desc", null);

        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));

        authenticateAsActor();

        assertThrows(ProjectNotFoundException.class,
                () -> projectService.updateProject(5L, request));

        verify(projectRepository, never()).save(any());
    }

    @Test
    void updateProject_OrgProject_Member_UpdatesFields() {
        Project existingProject = new Project();
        existingProject.setOrganization(organization);

        ProjectRequest request = new ProjectRequest("Updated Name", "desc", 1L);

        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        givenRole(actingUser, organization, OrganizationRole.MEMBER);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        authenticateAsActor();

        Project updated = projectService.updateProject(5L, request);

        assertEquals("Updated Name", updated.getName());
        assertEquals(organization, updated.getOrganization());
    }

    @Test
    void updateProject_OrgProject_Viewer_ThrowsException() {
        Project existingProject = new Project();
        existingProject.setOrganization(organization);

        ProjectRequest request = new ProjectRequest("Updated Name", "desc", 1L);

        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        givenRole(actingUser, organization, OrganizationRole.VIEWER);

        authenticateAsActor();

        assertThrows(InsufficientOrganizationPermissionException.class,
                () -> projectService.updateProject(5L, request));

        verify(projectRepository, never()).save(any());
    }

    @Test
    void updateProject_OrgProject_NonMember_ThrowsNotFound() {
        Project existingProject = new Project();
        existingProject.setOrganization(organization);

        ProjectRequest request = new ProjectRequest("Updated Name", "desc", 1L);

        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        givenNotMember(actingUser, organization);

        authenticateAsActor();

        assertThrows(ProjectNotFoundException.class,
                () -> projectService.updateProject(5L, request));

        verify(projectRepository, never()).save(any());
    }

    // --- updateProject tests: reassignment ---

    @Test
    void updateProject_PersonalToOrganization_ActorIsAdminOfTarget_Succeeds() {
        Project existingProject = new Project();
        existingProject.setOwner(actingUser);

        ProjectRequest request = new ProjectRequest("Name", "desc", 1L);

        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        givenRole(actingUser, organization, OrganizationRole.ADMIN);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        authenticateAsActor();

        Project updated = projectService.updateProject(5L, request);

        assertEquals(organization, updated.getOrganization());
        assertNull(updated.getOwner());
    }

    @Test
    void updateProject_PersonalToOrganization_ActorIsMemberOfTarget_ThrowsException() {
        Project existingProject = new Project();
        existingProject.setOwner(actingUser);

        ProjectRequest request = new ProjectRequest("Name", "desc", 1L);

        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        givenRole(actingUser, organization, OrganizationRole.MEMBER);

        authenticateAsActor();

        assertThrows(InsufficientOrganizationPermissionException.class,
                () -> projectService.updateProject(5L, request));

        verify(projectRepository, never()).save(any());
    }

    @Test
    void updateProject_PersonalToOrganization_ActorNotMemberOfTarget_ThrowsException() {
        Project existingProject = new Project();
        existingProject.setOwner(actingUser);

        ProjectRequest request = new ProjectRequest("Name", "desc", 1L);

        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        givenNotMember(actingUser, organization);

        authenticateAsActor();

        assertThrows(InsufficientOrganizationPermissionException.class,
                () -> projectService.updateProject(5L, request));

        verify(projectRepository, never()).save(any());
    }

    @Test
    void updateProject_OrganizationToPersonal_ActorIsAdminOfCurrent_Succeeds() {
        Project existingProject = new Project();
        existingProject.setOrganization(organization);

        ProjectRequest request = new ProjectRequest("Name", "desc", null);

        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        givenRole(actingUser, organization, OrganizationRole.ADMIN);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        authenticateAsActor();

        Project updated = projectService.updateProject(5L, request);

        assertEquals(actingUser, updated.getOwner());
        assertNull(updated.getOrganization());
    }

    @Test
    void updateProject_OrganizationToPersonal_ActorIsMemberOfCurrent_ThrowsException() {
        Project existingProject = new Project();
        existingProject.setOrganization(organization);

        ProjectRequest request = new ProjectRequest("Name", "desc", null);

        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        givenRole(actingUser, organization, OrganizationRole.MEMBER);

        authenticateAsActor();

        assertThrows(InsufficientOrganizationPermissionException.class,
                () -> projectService.updateProject(5L, request));

        verify(projectRepository, never()).save(any());
    }

    @Test
    void updateProject_OrganizationToDifferentOrganization_ActorIsAdminOfBoth_Succeeds() {
        Organization otherOrg = new Organization("Other Org");
        otherOrg.setId(2L);

        Project existingProject = new Project();
        existingProject.setOrganization(organization);

        ProjectRequest request = new ProjectRequest("Name", "desc", 2L);

        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        givenRole(actingUser, organization, OrganizationRole.ADMIN);
        when(organizationRepository.findById(2L)).thenReturn(Optional.of(otherOrg));
        givenRole(actingUser, otherOrg, OrganizationRole.OWNER);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        authenticateAsActor();

        Project updated = projectService.updateProject(5L, request);

        assertEquals(otherOrg, updated.getOrganization());
    }

    @Test
    void updateProject_OrganizationToDifferentOrganization_ActorNotAdminOfTarget_ThrowsException() {
        Organization otherOrg = new Organization("Other Org");
        otherOrg.setId(2L);

        Project existingProject = new Project();
        existingProject.setOrganization(organization);

        ProjectRequest request = new ProjectRequest("Name", "desc", 2L);

        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        givenRole(actingUser, organization, OrganizationRole.ADMIN);
        when(organizationRepository.findById(2L)).thenReturn(Optional.of(otherOrg));
        givenRole(actingUser, otherOrg, OrganizationRole.MEMBER);

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

    // --- deleteProject tests ---

    @Test
    void deleteProject_PersonalProject_Owner_Deletes() {
        Project existingProject = new Project();
        existingProject.setOwner(actingUser);

        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        when(taskRepository.existsByProjectId(5L)).thenReturn(false);

        authenticateAsActor();

        assertDoesNotThrow(() -> projectService.deleteProject(5L, false));

        verify(projectRepository).deleteById(5L);
    }

    @Test
    void deleteProject_PersonalProject_NonOwner_ThrowsNotFound() {
        User realOwner = new User();
        realOwner.setEmail("owner@acme.com");

        Project existingProject = new Project();
        existingProject.setOwner(realOwner);

        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));

        authenticateAsActor();

        assertThrows(ProjectNotFoundException.class,
                () -> projectService.deleteProject(5L, false));

        verify(projectRepository, never()).deleteById(any());
    }

    @Test
    void deleteProject_OrgProject_Admin_Deletes() {
        Project existingProject = new Project();
        existingProject.setOrganization(organization);

        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        givenRole(actingUser, organization, OrganizationRole.ADMIN);
        when(taskRepository.existsByProjectId(5L)).thenReturn(false);

        authenticateAsActor();

        assertDoesNotThrow(() -> projectService.deleteProject(5L, false));

        verify(projectRepository).deleteById(5L);
    }

    @Test
    void deleteProject_OrgProject_Member_ThrowsException() {
        Project existingProject = new Project();
        existingProject.setOrganization(organization);

        when(projectRepository.findById(5L)).thenReturn(Optional.of(existingProject));
        when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
        givenRole(actingUser, organization, OrganizationRole.MEMBER);

        authenticateAsActor();

        assertThrows(InsufficientOrganizationPermissionException.class,
                () -> projectService.deleteProject(5L, false));

        verify(projectRepository, never()).deleteById(any());
    }

    @Test
    void deleteProject_Missing_ThrowsNotFound() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ProjectNotFoundException.class,
                () -> projectService.deleteProject(99L, false));

        verify(projectRepository, never()).deleteById(any());
    }
}
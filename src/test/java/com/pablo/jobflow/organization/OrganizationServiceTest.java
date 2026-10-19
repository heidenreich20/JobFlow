package com.pablo.jobflow.organization;

import com.pablo.jobflow.auth.CurrentUserProvider;
import com.pablo.jobflow.user.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMembershipRepository membershipRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private OrganizationService organizationService;

    private User actingUser;
    private Organization organization;

    @BeforeEach
    void setUp() {
        actingUser = new User();
        actingUser.setEmail("actor@acme.com");

        organization = new Organization("Acme Corp");
        organization.setId(1L);
    }

    private void authenticateAsActor() {
        lenient().when(currentUserProvider.requireCurrentUser()).thenReturn(actingUser);
    }

    private void givenRole(User user, Organization org, OrganizationRole role) {
        when(membershipRepository.findByUserAndOrganization(user, org))
                .thenReturn(Optional.of(new OrganizationMembership(user, org, role)));
    }

    private void givenNotMember(User user, Organization org) {
        when(membershipRepository.findByUserAndOrganization(user, org))
                .thenReturn(Optional.empty());
    }

    // --- create ---

    @Test
    void createOrganization_Valid_ReturnsResponse() {
        OrganizationRequest request = new OrganizationRequest("Acme Corp");

        Organization savedOrg = new Organization("Acme Corp");
        savedOrg.setId(1L);

        when(organizationRepository.existsByName("Acme Corp")).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenReturn(savedOrg);
        authenticateAsActor();

        OrganizationResponse response = organizationService.createOrganization(request);

        assertNotNull(response);
        assertEquals("Acme Corp", response.name());
        assertEquals(1L, response.id());

        verify(membershipRepository).save(
                argThat(membership -> membership.getUser() == actingUser
                        && membership.getOrganization() == savedOrg
                        && membership.getRole() == OrganizationRole.OWNER));
    }

    @Test
    void createOrganization_DuplicateName_ThrowsException() {
        OrganizationRequest request = new OrganizationRequest("Acme Corp");
        when(organizationRepository.existsByName("Acme Corp")).thenReturn(true);

        assertThrows(DuplicateOrganizationNameException.class,
                () -> organizationService.createOrganization(request));
        verify(organizationRepository, never()).save(any());
        verify(membershipRepository, never()).save(any());
    }

    // --- get by id ---

    @Test
    void getOrganizationById_Member_ReturnsResponse() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        authenticateAsActor();
        givenRole(actingUser, organization, OrganizationRole.VIEWER);

        OrganizationResponse response = organizationService.getOrganizationById(1L);

        assertEquals("Acme Corp", response.name());
    }

    @Test
    void getOrganizationById_Missing_ThrowsException() {
        when(organizationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(OrganizationNotFoundException.class,
                () -> organizationService.getOrganizationById(99L));
    }

    @Test
    void getOrganizationById_NotMember_ThrowsNotFound() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        authenticateAsActor();
        givenNotMember(actingUser, organization);

        assertThrows(OrganizationNotFoundException.class,
                () -> organizationService.getOrganizationById(1L));
    }

    // --- list ---

    @Test
    void getAllOrganizations_ReturnsOnlyMemberships() {
        Organization other = new Organization("Other Corp");
        other.setId(2L);

        authenticateAsActor();
        when(membershipRepository.findByUser(actingUser)).thenReturn(List.of(
                new OrganizationMembership(actingUser, organization, OrganizationRole.MEMBER)));

        List<OrganizationResponse> responses = organizationService.getAllOrganizations();

        assertEquals(1, responses.size());
        assertEquals("Acme Corp", responses.get(0).name());
        verify(organizationRepository, never()).findAll();
    }

    @Test
    void getAllOrganizations_NoMemberships_ReturnsEmpty() {
        authenticateAsActor();
        when(membershipRepository.findByUser(actingUser)).thenReturn(List.of());

        assertTrue(organizationService.getAllOrganizations().isEmpty());
    }

    // --- update ---

    @Test
    void updateOrganization_Owner_ReturnsUpdatedResponse() {
        OrganizationRequest request = new OrganizationRequest("New Name");

        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        when(organizationRepository.existsByName("New Name")).thenReturn(false);
        authenticateAsActor();
        givenRole(actingUser, organization, OrganizationRole.OWNER);

        OrganizationResponse response = organizationService.updateOrganization(1L, request);

        assertEquals(1L, response.id());
        assertEquals("New Name", response.name());
        verify(organizationRepository, never()).save(any(Organization.class));
    }

    @Test
    void updateOrganization_Admin_Succeeds() {
        OrganizationRequest request = new OrganizationRequest("New Name");

        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        when(organizationRepository.existsByName("New Name")).thenReturn(false);
        authenticateAsActor();
        givenRole(actingUser, organization, OrganizationRole.ADMIN);

        OrganizationResponse response = organizationService.updateOrganization(1L, request);

        assertEquals("New Name", response.name());
    }

    @Test
    void updateOrganization_Member_ThrowsForbidden() {
        OrganizationRequest request = new OrganizationRequest("New Name");

        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        authenticateAsActor();
        givenRole(actingUser, organization, OrganizationRole.MEMBER);

        assertThrows(InsufficientOrganizationPermissionException.class,
                () -> organizationService.updateOrganization(1L, request));
    }

    @Test
    void updateOrganization_Viewer_ThrowsForbidden() {
        OrganizationRequest request = new OrganizationRequest("New Name");

        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        authenticateAsActor();
        givenRole(actingUser, organization, OrganizationRole.VIEWER);

        assertThrows(InsufficientOrganizationPermissionException.class,
                () -> organizationService.updateOrganization(1L, request));
    }

    @Test
    void updateOrganization_NotMember_ThrowsNotFound() {
        OrganizationRequest request = new OrganizationRequest("New Name");

        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        authenticateAsActor();
        givenNotMember(actingUser, organization);

        assertThrows(OrganizationNotFoundException.class,
                () -> organizationService.updateOrganization(1L, request));
    }

    @Test
    void updateOrganization_DuplicateName_ThrowsException() {
        OrganizationRequest request = new OrganizationRequest("Existing Other Name");

        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        when(organizationRepository.existsByName("Existing Other Name")).thenReturn(true);
        authenticateAsActor();
        givenRole(actingUser, organization, OrganizationRole.OWNER);

        assertThrows(DuplicateOrganizationNameException.class,
                () -> organizationService.updateOrganization(1L, request));
    }

    @Test
    void updateOrganization_Missing_ThrowsException() {
        OrganizationRequest request = new OrganizationRequest("New Name");
        when(organizationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(OrganizationNotFoundException.class,
                () -> organizationService.updateOrganization(99L, request));
    }

    // --- delete ---

    @Test
    void deleteOrganization_Owner_DeletesSuccessfully() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        authenticateAsActor();
        givenRole(actingUser, organization, OrganizationRole.OWNER);

        assertDoesNotThrow(() -> organizationService.deleteOrganization(1L));
        verify(organizationRepository).deleteById(1L);
    }

    @Test
    void deleteOrganization_Admin_ThrowsForbidden() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        authenticateAsActor();
        givenRole(actingUser, organization, OrganizationRole.ADMIN);

        assertThrows(InsufficientOrganizationPermissionException.class,
                () -> organizationService.deleteOrganization(1L));
        verify(organizationRepository, never()).deleteById(any());
    }

    @Test
    void deleteOrganization_Member_ThrowsForbidden() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        authenticateAsActor();
        givenRole(actingUser, organization, OrganizationRole.MEMBER);

        assertThrows(InsufficientOrganizationPermissionException.class,
                () -> organizationService.deleteOrganization(1L));
        verify(organizationRepository, never()).deleteById(any());
    }

    @Test
    void deleteOrganization_Viewer_ThrowsForbidden() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        authenticateAsActor();
        givenRole(actingUser, organization, OrganizationRole.VIEWER);

        assertThrows(InsufficientOrganizationPermissionException.class,
                () -> organizationService.deleteOrganization(1L));
        verify(organizationRepository, never()).deleteById(any());
    }

    @Test
    void deleteOrganization_NotMember_ThrowsNotFound() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        authenticateAsActor();
        givenNotMember(actingUser, organization);

        assertThrows(OrganizationNotFoundException.class,
                () -> organizationService.deleteOrganization(1L));
        verify(organizationRepository, never()).deleteById(any());
    }

    @Test
    void deleteOrganization_Missing_ThrowsException() {
        when(organizationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(OrganizationNotFoundException.class,
                () -> organizationService.deleteOrganization(99L));
        verify(organizationRepository, never()).deleteById(any());
    }
}

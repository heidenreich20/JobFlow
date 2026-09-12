package com.pablo.jobflow.organization;

import com.pablo.jobflow.user.User;
import com.pablo.jobflow.user.UserRepository;

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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OrganizationService organizationService;

    @Test
    void createOrganization_Valid_ReturnsResponse() {

        OrganizationRequest request = new OrganizationRequest("Acme Corp");

        User user = new User();
        user.setEmail("owner@acme.com");

        Organization savedOrg = new Organization("Acme Corp");
        savedOrg.setId(1L);

        when(organizationRepository.existsByName("Acme Corp"))
                .thenReturn(false);

        when(userRepository.findByEmail("owner@acme.com"))
                .thenReturn(Optional.of(user));

        when(organizationRepository.save(any(Organization.class)))
                .thenReturn(savedOrg);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "owner@acme.com",
                null);

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        try {
            OrganizationResponse response = organizationService.createOrganization(request);

            assertNotNull(response);
            assertEquals("Acme Corp", response.name());
            assertEquals(1L, response.id());

            verify(membershipRepository).save(
                    argThat(membership -> membership.getUser() == user
                            && membership.getOrganization() == savedOrg
                            && membership.getRole() == OrganizationRole.OWNER));

        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void createOrganization_DuplicateName_ThrowsException() {
        OrganizationRequest request = new OrganizationRequest("Acme Corp");
        when(organizationRepository.existsByName("Acme Corp")).thenReturn(true);

        assertThrows(DuplicateOrganizationNameException.class, () -> organizationService.createOrganization(request));
        verify(organizationRepository, never()).save(any());
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void createOrganization_AuthenticatedUserMissing_ThrowsException() {
        OrganizationRequest request = new OrganizationRequest("Acme Corp");

        when(organizationRepository.existsByName("Acme Corp"))
                .thenReturn(false);

        when(userRepository.findByEmail("ghost@acme.com"))
                .thenReturn(Optional.empty());

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "ghost@acme.com",
                null);

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        try {
            assertThrows(IllegalStateException.class,
                    () -> organizationService.createOrganization(request));

            verify(organizationRepository, never()).save(any());
            verify(membershipRepository, never()).save(any());

        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void getOrganizationById_Existing_ReturnsResponse() {
        Organization org = new Organization("Acme Corp");
        org.setId(1L);

        when(organizationRepository.findById(1L)).thenReturn(Optional.of(org));

        OrganizationResponse response = organizationService.getOrganizationById(1L);

        assertEquals("Acme Corp", response.name());
    }

    @Test
    void getOrganizationById_Missing_ThrowsException() {
        when(organizationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(OrganizationNotFoundException.class, () -> organizationService.getOrganizationById(99L));
    }

    @Test
    void getAllOrganizations_ReturnsList() {
        Organization org = new Organization("Acme Corp");
        org.setId(1L);
        when(organizationRepository.findAll()).thenReturn(List.of(org));

        List<OrganizationResponse> responses = organizationService.getAllOrganizations();

        assertEquals(1, responses.size());
        assertEquals("Acme Corp", responses.get(0).name());
    }

    @Test
    void updateOrganization_Valid_ReturnsUpdatedResponse() {
        Organization organization = new Organization("Old Name");
        organization.setId(1L);

        OrganizationRequest request = new OrganizationRequest("New Name");

        when(organizationRepository.findById(1L))
                .thenReturn(Optional.of(organization));

        when(organizationRepository.existsByName("New Name"))
                .thenReturn(false);

        OrganizationResponse response = organizationService.updateOrganization(1L, request);

        assertEquals(1L, response.id());
        assertEquals("New Name", response.name());

        verify(organizationRepository).findById(1L);
        verify(organizationRepository).existsByName("New Name");
        verify(organizationRepository, never()).save(any(Organization.class));
    }

    @Test
    void updateOrganization_DuplicateName_ThrowsException() {
        Organization existingOrg = new Organization("Old Name");
        existingOrg.setId(1L);
        OrganizationRequest request = new OrganizationRequest("Existing Other Name");

        when(organizationRepository.findById(1L)).thenReturn(Optional.of(existingOrg));
        when(organizationRepository.existsByName("Existing Other Name")).thenReturn(true);

        assertThrows(DuplicateOrganizationNameException.class,
                () -> organizationService.updateOrganization(1L, request));
    }

    @Test
    void deleteOrganization_Existing_DeletesSuccessfully() {
        when(organizationRepository.existsById(1L)).thenReturn(true);

        assertDoesNotThrow(() -> organizationService.deleteOrganization(1L));
        verify(organizationRepository).deleteById(1L);
    }

    @Test
    void deleteOrganization_Missing_ThrowsException() {
        when(organizationRepository.existsById(99L)).thenReturn(false);

        assertThrows(OrganizationNotFoundException.class, () -> organizationService.deleteOrganization(99L));
        verify(organizationRepository, never()).deleteById(any());
    }
}
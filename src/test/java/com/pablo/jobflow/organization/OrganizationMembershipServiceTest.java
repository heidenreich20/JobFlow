package com.pablo.jobflow.organization;

import com.pablo.jobflow.user.User;
import com.pablo.jobflow.user.UserNotFoundException;
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
class OrganizationMembershipServiceTest {

        @Mock
        private OrganizationRepository organizationRepository;

        @Mock
        private OrganizationMembershipRepository membershipRepository;

        @Mock
        private UserRepository userRepository;

        @InjectMocks
        private OrganizationMembershipService membershipService;

        private Organization organization;
        private User actingUser;
        private User targetUser;

        @BeforeEach
        void setUp() {
                organization = new Organization("Acme Corp");
                organization.setId(1L);

                actingUser = new User();
                actingUser.setEmail("actor@acme.com");

                targetUser = new User();
                targetUser.setEmail("new@acme.com");
        }

        @AfterEach
        void clearSecurityContext() {
                SecurityContextHolder.clearContext();
        }

        private void authenticateAsActor() {
                Authentication authentication = new UsernamePasswordAuthenticationToken("actor@acme.com", null);
                SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        private void givenActingRole(OrganizationRole role) {
                when(userRepository.findByEmail("actor@acme.com"))
                                .thenReturn(Optional.of(actingUser));

                when(membershipRepository.findByUserAndOrganization(actingUser, organization))
                                .thenReturn(Optional.of(new OrganizationMembership(actingUser, organization, role)));
        }

        @Test
        void addMember_ActorIsOwner_AddsMemberSuccessfully() {
                AddMemberRequest request = new AddMemberRequest("new@acme.com", OrganizationRole.MEMBER);

                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                givenActingRole(OrganizationRole.OWNER);
                when(userRepository.findByEmail("new@acme.com")).thenReturn(Optional.of(targetUser));
                when(membershipRepository.existsByUserAndOrganization(targetUser, organization))
                                .thenReturn(false);
                when(membershipRepository.save(any(OrganizationMembership.class)))
                                .thenReturn(new OrganizationMembership(targetUser, organization,
                                                OrganizationRole.MEMBER));

                authenticateAsActor();

                OrganizationMembershipResponse response = membershipService.addMember(1L, request);

                assertEquals("new@acme.com", response.email());
                assertEquals(OrganizationRole.MEMBER, response.role());
                verify(membershipRepository).save(any(OrganizationMembership.class));
        }

        @Test
        void addMember_ActorIsAdmin_AddsMemberSuccessfully() {
                AddMemberRequest request = new AddMemberRequest("new@acme.com", OrganizationRole.MEMBER);

                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                givenActingRole(OrganizationRole.ADMIN);
                when(userRepository.findByEmail("new@acme.com")).thenReturn(Optional.of(targetUser));
                when(membershipRepository.existsByUserAndOrganization(targetUser, organization))
                                .thenReturn(false);
                when(membershipRepository.save(any(OrganizationMembership.class)))
                                .thenReturn(new OrganizationMembership(targetUser, organization,
                                                OrganizationRole.MEMBER));

                authenticateAsActor();

                OrganizationMembershipResponse response = membershipService.addMember(1L, request);

                assertEquals(OrganizationRole.MEMBER, response.role());
        }

        @Test
        void addMember_ActorIsOwner_AddingAsOwner_Succeeds() {
                AddMemberRequest request = new AddMemberRequest("new@acme.com", OrganizationRole.OWNER);

                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                givenActingRole(OrganizationRole.OWNER);
                when(userRepository.findByEmail("new@acme.com")).thenReturn(Optional.of(targetUser));
                when(membershipRepository.existsByUserAndOrganization(targetUser, organization))
                                .thenReturn(false);
                when(membershipRepository.save(any(OrganizationMembership.class)))
                                .thenReturn(new OrganizationMembership(targetUser, organization,
                                                OrganizationRole.OWNER));

                authenticateAsActor();

                OrganizationMembershipResponse response = membershipService.addMember(1L, request);

                assertEquals(OrganizationRole.OWNER, response.role());
        }

        @Test
        void addMember_ActorIsAdmin_AddingAsOwner_ThrowsException() {
                AddMemberRequest request = new AddMemberRequest("new@acme.com", OrganizationRole.OWNER);

                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                givenActingRole(OrganizationRole.ADMIN);

                authenticateAsActor();

                assertThrows(InsufficientOrganizationPermissionException.class,
                                () -> membershipService.addMember(1L, request));

                verify(membershipRepository, never()).save(any());
        }

        @Test
        void addMember_ActorIsMember_ThrowsException() {
                AddMemberRequest request = new AddMemberRequest("new@acme.com", OrganizationRole.VIEWER);

                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                givenActingRole(OrganizationRole.MEMBER);

                authenticateAsActor();

                assertThrows(InsufficientOrganizationPermissionException.class,
                                () -> membershipService.addMember(1L, request));

                verify(membershipRepository, never()).save(any());
        }

        @Test
        void addMember_ActorIsViewer_ThrowsException() {
                AddMemberRequest request = new AddMemberRequest("new@acme.com", OrganizationRole.VIEWER);

                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                givenActingRole(OrganizationRole.VIEWER);

                authenticateAsActor();

                assertThrows(InsufficientOrganizationPermissionException.class,
                                () -> membershipService.addMember(1L, request));

                verify(membershipRepository, never()).save(any());
        }

        @Test
        void addMember_ActorNotAMember_ThrowsException() {
                AddMemberRequest request = new AddMemberRequest("new@acme.com", OrganizationRole.MEMBER);

                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
                when(membershipRepository.findByUserAndOrganization(actingUser, organization))
                                .thenReturn(Optional.empty());

                authenticateAsActor();

                assertThrows(InsufficientOrganizationPermissionException.class,
                                () -> membershipService.addMember(1L, request));

                verify(membershipRepository, never()).save(any());
        }

        @Test
        void addMember_OrganizationMissing_ThrowsException() {
                AddMemberRequest request = new AddMemberRequest("new@acme.com", OrganizationRole.MEMBER);

                when(organizationRepository.findById(99L)).thenReturn(Optional.empty());

                assertThrows(OrganizationNotFoundException.class,
                                () -> membershipService.addMember(99L, request));

                verify(membershipRepository, never()).save(any());
        }

        @Test
        void addMember_TargetUserMissing_ThrowsException() {
                AddMemberRequest request = new AddMemberRequest("ghost@acme.com", OrganizationRole.MEMBER);

                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                givenActingRole(OrganizationRole.OWNER);
                when(userRepository.findByEmail("ghost@acme.com")).thenReturn(Optional.empty());

                authenticateAsActor();

                assertThrows(UserNotFoundException.class,
                                () -> membershipService.addMember(1L, request));

                verify(membershipRepository, never()).save(any());
        }

        @Test
        void addMember_TargetAlreadyMember_ThrowsException() {
                AddMemberRequest request = new AddMemberRequest("new@acme.com", OrganizationRole.MEMBER);

                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                givenActingRole(OrganizationRole.OWNER);
                when(userRepository.findByEmail("new@acme.com")).thenReturn(Optional.of(targetUser));
                when(membershipRepository.existsByUserAndOrganization(targetUser, organization))
                                .thenReturn(true);

                authenticateAsActor();

                assertThrows(DuplicateMembershipException.class,
                                () -> membershipService.addMember(1L, request));

                verify(membershipRepository, never()).save(any());
        }

        // --- changeRole tests ---

        private OrganizationMembership givenTargetMembership(OrganizationRole role) {
                when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));

                OrganizationMembership targetMembership = new OrganizationMembership(targetUser, organization, role);
                when(membershipRepository.findByUserAndOrganization(targetUser, organization))
                                .thenReturn(Optional.of(targetMembership));

                return targetMembership;
        }

        @Test
        void changeRole_ActorIsOwner_ChangesNonOwnerRoleSuccessfully() {
                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                givenActingRole(OrganizationRole.OWNER);
                givenTargetMembership(OrganizationRole.MEMBER);

                ChangeRoleRequest request = new ChangeRoleRequest(OrganizationRole.ADMIN);

                authenticateAsActor();

                OrganizationMembershipResponse response = membershipService.changeRole(1L, 2L, request);

                assertEquals(OrganizationRole.ADMIN, response.role());
        }

        @Test
        void changeRole_ActorIsAdmin_ChangesNonOwnerRoleSuccessfully() {
                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                givenActingRole(OrganizationRole.ADMIN);
                givenTargetMembership(OrganizationRole.VIEWER);

                ChangeRoleRequest request = new ChangeRoleRequest(OrganizationRole.MEMBER);

                authenticateAsActor();

                OrganizationMembershipResponse response = membershipService.changeRole(1L, 2L, request);

                assertEquals(OrganizationRole.MEMBER, response.role());
        }

        @Test
        void changeRole_ActorIsOwner_PromotingToOwner_Succeeds() {
                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                givenActingRole(OrganizationRole.OWNER);
                givenTargetMembership(OrganizationRole.ADMIN);

                ChangeRoleRequest request = new ChangeRoleRequest(OrganizationRole.OWNER);

                authenticateAsActor();

                OrganizationMembershipResponse response = membershipService.changeRole(1L, 2L, request);

                assertEquals(OrganizationRole.OWNER, response.role());
        }

        @Test
        void changeRole_ActorIsAdmin_PromotingToOwner_ThrowsException() {
                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                givenActingRole(OrganizationRole.ADMIN);
                givenTargetMembership(OrganizationRole.MEMBER);

                ChangeRoleRequest request = new ChangeRoleRequest(OrganizationRole.OWNER);

                authenticateAsActor();

                assertThrows(InsufficientOrganizationPermissionException.class,
                                () -> membershipService.changeRole(1L, 2L, request));
        }

        @Test
        void changeRole_ActorIsAdmin_TargetIsOwner_ThrowsException() {
                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                givenActingRole(OrganizationRole.ADMIN);
                givenTargetMembership(OrganizationRole.OWNER);

                ChangeRoleRequest request = new ChangeRoleRequest(OrganizationRole.MEMBER);

                authenticateAsActor();

                assertThrows(InsufficientOrganizationPermissionException.class,
                                () -> membershipService.changeRole(1L, 2L, request));
        }

        @Test
        void changeRole_ActorIsOwner_DemotingOwner_MultipleOwnersExist_Succeeds() {
                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                givenActingRole(OrganizationRole.OWNER);
                givenTargetMembership(OrganizationRole.OWNER);
                when(membershipRepository.countByOrganizationAndRole(organization, OrganizationRole.OWNER))
                                .thenReturn(2L);

                ChangeRoleRequest request = new ChangeRoleRequest(OrganizationRole.ADMIN);

                authenticateAsActor();

                OrganizationMembershipResponse response = membershipService.changeRole(1L, 2L, request);

                assertEquals(OrganizationRole.ADMIN, response.role());
        }

        @Test
        void changeRole_ActorIsOwner_DemotingLastOwner_ThrowsException() {
                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                givenActingRole(OrganizationRole.OWNER);
                givenTargetMembership(OrganizationRole.OWNER);
                when(membershipRepository.countByOrganizationAndRole(organization, OrganizationRole.OWNER))
                                .thenReturn(1L);

                ChangeRoleRequest request = new ChangeRoleRequest(OrganizationRole.ADMIN);

                authenticateAsActor();

                assertThrows(LastOwnerException.class,
                                () -> membershipService.changeRole(1L, 2L, request));
        }

        @Test
        void changeRole_ActorIsMember_ThrowsException() {
                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                givenActingRole(OrganizationRole.MEMBER);

                ChangeRoleRequest request = new ChangeRoleRequest(OrganizationRole.ADMIN);

                authenticateAsActor();

                assertThrows(InsufficientOrganizationPermissionException.class,
                                () -> membershipService.changeRole(1L, 2L, request));
        }

        @Test
        void changeRole_ActorNotAMember_ThrowsException() {
                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
                when(membershipRepository.findByUserAndOrganization(actingUser, organization))
                                .thenReturn(Optional.empty());

                ChangeRoleRequest request = new ChangeRoleRequest(OrganizationRole.ADMIN);

                authenticateAsActor();

                assertThrows(InsufficientOrganizationPermissionException.class,
                                () -> membershipService.changeRole(1L, 2L, request));
        }

        @Test
        void changeRole_OrganizationMissing_ThrowsException() {
                when(organizationRepository.findById(99L)).thenReturn(Optional.empty());

                ChangeRoleRequest request = new ChangeRoleRequest(OrganizationRole.ADMIN);

                assertThrows(OrganizationNotFoundException.class,
                                () -> membershipService.changeRole(99L, 2L, request));
        }

        @Test
        void changeRole_TargetUserMissing_ThrowsException() {
                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                givenActingRole(OrganizationRole.OWNER);
                when(userRepository.findById(99L)).thenReturn(Optional.empty());

                ChangeRoleRequest request = new ChangeRoleRequest(OrganizationRole.ADMIN);

                authenticateAsActor();

                assertThrows(UserNotFoundException.class,
                                () -> membershipService.changeRole(1L, 99L, request));
        }

        @Test
        void changeRole_TargetMembershipMissing_ThrowsException() {
                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                givenActingRole(OrganizationRole.OWNER);

                when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));
                when(membershipRepository.findByUserAndOrganization(targetUser, organization))
                                .thenReturn(Optional.empty());

                ChangeRoleRequest request = new ChangeRoleRequest(OrganizationRole.ADMIN);

                authenticateAsActor();

                assertThrows(MembershipNotFoundException.class,
                                () -> membershipService.changeRole(1L, 2L, request));
        }

        @Test
        void removeMember_ActorIsOwner_RemovesNonOwnerSuccessfully() {
                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                givenActingRole(OrganizationRole.OWNER);
                givenTargetMembership(OrganizationRole.MEMBER);

                authenticateAsActor();

                assertDoesNotThrow(() -> membershipService.removeMember(1L, 2L));

                verify(membershipRepository).delete(any(OrganizationMembership.class));
        }

        @Test
        void removeMember_ActorIsAdmin_RemovesNonOwnerSuccessfully() {
                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                givenActingRole(OrganizationRole.ADMIN);
                givenTargetMembership(OrganizationRole.VIEWER);

                authenticateAsActor();

                assertDoesNotThrow(() -> membershipService.removeMember(1L, 2L));

                verify(membershipRepository).delete(any(OrganizationMembership.class));
        }

        @Test
        void removeMember_ActorIsOwner_RemovingOwner_MultipleOwnersExist_Succeeds() {
                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                givenActingRole(OrganizationRole.OWNER);
                givenTargetMembership(OrganizationRole.OWNER);
                when(membershipRepository.countByOrganizationAndRole(organization, OrganizationRole.OWNER))
                                .thenReturn(2L);

                authenticateAsActor();

                assertDoesNotThrow(() -> membershipService.removeMember(1L, 2L));

                verify(membershipRepository).delete(any(OrganizationMembership.class));
        }

        @Test
        void removeMember_ActorIsOwner_RemovingLastOwner_ThrowsException() {
                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                givenActingRole(OrganizationRole.OWNER);
                givenTargetMembership(OrganizationRole.OWNER);
                when(membershipRepository.countByOrganizationAndRole(organization, OrganizationRole.OWNER))
                                .thenReturn(1L);

                authenticateAsActor();

                assertThrows(LastOwnerException.class,
                                () -> membershipService.removeMember(1L, 2L));

                verify(membershipRepository, never()).delete(any());
        }

        @Test
        void removeMember_ActorIsAdmin_TargetIsOwner_ThrowsException() {
                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                givenActingRole(OrganizationRole.ADMIN);
                givenTargetMembership(OrganizationRole.OWNER);

                authenticateAsActor();

                assertThrows(InsufficientOrganizationPermissionException.class,
                                () -> membershipService.removeMember(1L, 2L));

                verify(membershipRepository, never()).delete(any());
        }

        @Test
        void removeMember_ActorIsMember_ThrowsException() {
                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                givenActingRole(OrganizationRole.MEMBER);

                authenticateAsActor();

                assertThrows(InsufficientOrganizationPermissionException.class,
                                () -> membershipService.removeMember(1L, 2L));

                verify(membershipRepository, never()).delete(any());
        }

        @Test
        void removeMember_ActorNotAMember_ThrowsException() {
                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                when(userRepository.findByEmail("actor@acme.com")).thenReturn(Optional.of(actingUser));
                when(membershipRepository.findByUserAndOrganization(actingUser, organization))
                                .thenReturn(Optional.empty());

                authenticateAsActor();

                assertThrows(InsufficientOrganizationPermissionException.class,
                                () -> membershipService.removeMember(1L, 2L));

                verify(membershipRepository, never()).delete(any());
        }

        @Test
        void removeMember_OrganizationMissing_ThrowsException() {
                when(organizationRepository.findById(99L)).thenReturn(Optional.empty());

                assertThrows(OrganizationNotFoundException.class,
                                () -> membershipService.removeMember(99L, 2L));

                verify(membershipRepository, never()).delete(any());
        }

        @Test
        void removeMember_TargetUserMissing_ThrowsException() {
                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                givenActingRole(OrganizationRole.OWNER);
                when(userRepository.findById(99L)).thenReturn(Optional.empty());

                authenticateAsActor();

                assertThrows(UserNotFoundException.class,
                                () -> membershipService.removeMember(1L, 99L));

                verify(membershipRepository, never()).delete(any());
        }

        @Test
        void removeMember_TargetMembershipMissing_ThrowsException() {
                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                givenActingRole(OrganizationRole.OWNER);

                when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));
                when(membershipRepository.findByUserAndOrganization(targetUser, organization))
                                .thenReturn(Optional.empty());

                authenticateAsActor();

                assertThrows(MembershipNotFoundException.class,
                                () -> membershipService.removeMember(1L, 2L));

                verify(membershipRepository, never()).delete(any());
        }

        @Test
        void removeMember_SelfRemoval_OwnerRemovesSelf_MultipleOwnersExist_Succeeds() {
                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                givenActingRole(OrganizationRole.OWNER);
                when(userRepository.findById(1L)).thenReturn(Optional.of(actingUser));
                when(membershipRepository.countByOrganizationAndRole(organization, OrganizationRole.OWNER))
                                .thenReturn(2L);

                authenticateAsActor();

                assertDoesNotThrow(() -> membershipService.removeMember(1L, 1L));

                verify(membershipRepository).delete(any(OrganizationMembership.class));
        }

        @Test
        void removeMember_SelfRemoval_LastOwner_ThrowsException() {
                when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
                givenActingRole(OrganizationRole.OWNER);
                when(userRepository.findById(1L)).thenReturn(Optional.of(actingUser));
                when(membershipRepository.countByOrganizationAndRole(organization, OrganizationRole.OWNER))
                                .thenReturn(1L);

                authenticateAsActor();

                assertThrows(LastOwnerException.class,
                                () -> membershipService.removeMember(1L, 1L));

                verify(membershipRepository, never()).delete(any());
        }
}
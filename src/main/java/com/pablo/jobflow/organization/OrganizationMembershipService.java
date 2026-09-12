package com.pablo.jobflow.organization;

import com.pablo.jobflow.user.User;
import com.pablo.jobflow.user.UserNotFoundException;
import com.pablo.jobflow.user.UserRepository;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationMembershipService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    public OrganizationMembershipService(
            OrganizationRepository organizationRepository,
            OrganizationMembershipRepository membershipRepository,
            UserRepository userRepository) {

        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public OrganizationMembershipResponse addMember(
            Long organizationId,
            AddMemberRequest request) {

        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));

        User actingUser = getAuthenticatedUser();

        OrganizationRole actingRole = membershipRepository
                .findByUserAndOrganization(actingUser, organization)
                .map(OrganizationMembership::getRole)
                .orElseThrow(() -> new InsufficientOrganizationPermissionException(
                        "You are not a member of this organization."));

        if (actingRole != OrganizationRole.OWNER && actingRole != OrganizationRole.ADMIN) {
            throw new InsufficientOrganizationPermissionException(
                    "Only an OWNER or ADMIN can add members.");
        }

        if (request.role() == OrganizationRole.OWNER && actingRole != OrganizationRole.OWNER) {
            throw new InsufficientOrganizationPermissionException(
                    "Only an OWNER can add a member as OWNER.");
        }

        User targetUser = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UserNotFoundException(request.email()));

        if (membershipRepository.existsByUserAndOrganization(targetUser, organization)) {
            throw new DuplicateMembershipException(request.email(), organization.getName());
        }

        OrganizationMembership membership =
                new OrganizationMembership(targetUser, organization, request.role());

        OrganizationMembership saved = membershipRepository.save(membership);

        return mapToResponse(saved);
    }

    @Transactional
    public OrganizationMembershipResponse changeRole(
            Long organizationId,
            Long userId,
            ChangeRoleRequest request) {

        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));

        User actingUser = getAuthenticatedUser();

        OrganizationRole actingRole = membershipRepository
                .findByUserAndOrganization(actingUser, organization)
                .map(OrganizationMembership::getRole)
                .orElseThrow(() -> new InsufficientOrganizationPermissionException(
                        "You are not a member of this organization."));

        if (actingRole != OrganizationRole.OWNER && actingRole != OrganizationRole.ADMIN) {
            throw new InsufficientOrganizationPermissionException(
                    "Only an OWNER or ADMIN can change member roles.");
        }

        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        OrganizationMembership targetMembership = membershipRepository
                .findByUserAndOrganization(targetUser, organization)
                .orElseThrow(() -> new MembershipNotFoundException(userId, organizationId));

        OrganizationRole currentRole = targetMembership.getRole();
        OrganizationRole newRole = request.role();

        if (currentRole == OrganizationRole.OWNER && actingRole != OrganizationRole.OWNER) {
            throw new InsufficientOrganizationPermissionException(
                    "Only an OWNER can change another OWNER's role.");
        }

        if (newRole == OrganizationRole.OWNER && actingRole != OrganizationRole.OWNER) {
            throw new InsufficientOrganizationPermissionException(
                    "Only an OWNER can promote a member to OWNER.");
        }

        if (currentRole == OrganizationRole.OWNER && newRole != OrganizationRole.OWNER) {
            long ownerCount = membershipRepository
                    .countByOrganizationAndRole(organization, OrganizationRole.OWNER);

            if (ownerCount <= 1) {
                throw new LastOwnerException(
                        "Cannot change role: organization must have at least one OWNER.");
            }
        }

        targetMembership.setRole(newRole);

        return mapToResponse(targetMembership);
    }

    @Transactional
    public void removeMember(Long organizationId, Long userId) {

        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));

        User actingUser = getAuthenticatedUser();

        OrganizationRole actingRole = membershipRepository
                .findByUserAndOrganization(actingUser, organization)
                .map(OrganizationMembership::getRole)
                .orElseThrow(() -> new InsufficientOrganizationPermissionException(
                        "You are not a member of this organization."));

        if (actingRole != OrganizationRole.OWNER && actingRole != OrganizationRole.ADMIN) {
            throw new InsufficientOrganizationPermissionException(
                    "Only an OWNER or ADMIN can remove members.");
        }

        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        OrganizationMembership targetMembership = membershipRepository
                .findByUserAndOrganization(targetUser, organization)
                .orElseThrow(() -> new MembershipNotFoundException(userId, organizationId));

        if (targetMembership.getRole() == OrganizationRole.OWNER) {

            if (actingRole != OrganizationRole.OWNER) {
                throw new InsufficientOrganizationPermissionException(
                        "Only an OWNER can remove another OWNER.");
            }

            long ownerCount = membershipRepository
                    .countByOrganizationAndRole(organization, OrganizationRole.OWNER);

            if (ownerCount <= 1) {
                throw new LastOwnerException(
                        "Cannot remove: organization must have at least one OWNER.");
            }
        }

        membershipRepository.delete(targetMembership);
    }

    private User getAuthenticatedUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String email = authentication.getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user does not exist"));
    }

    private OrganizationMembershipResponse mapToResponse(OrganizationMembership membership) {
        return new OrganizationMembershipResponse(
                membership.getId(),
                membership.getUser().getId(),
                membership.getUser().getEmail(),
                membership.getRole(),
                membership.getCreatedAt()
        );
    }
}
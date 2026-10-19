package com.pablo.jobflow.organization;

import com.pablo.jobflow.auth.CurrentUserProvider;
import com.pablo.jobflow.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final CurrentUserProvider currentUserProvider;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            OrganizationMembershipRepository membershipRepository,
            CurrentUserProvider currentUserProvider) {

        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public OrganizationResponse createOrganization(OrganizationRequest request) {
        if (organizationRepository.existsByName(request.name())) {
            throw new DuplicateOrganizationNameException(request.name());
        }

        User authenticatedUser = currentUserProvider.requireCurrentUser();

        Organization organization = new Organization(request.name());
        Organization savedOrganization = organizationRepository.save(organization);

        OrganizationMembership membership = new OrganizationMembership(
                authenticatedUser,
                savedOrganization,
                OrganizationRole.OWNER
        );

        membershipRepository.save(membership);

        return mapToResponse(savedOrganization);
    }

    @Transactional(readOnly = true)
    public OrganizationResponse getOrganizationById(Long id) {
        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new OrganizationNotFoundException(id));

        User actingUser = currentUserProvider.requireCurrentUser();
        requireCanView(actingUser, organization, id);

        return mapToResponse(organization);
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> getAllOrganizations() {
        User actingUser = currentUserProvider.requireCurrentUser();

        return membershipRepository.findByUser(actingUser).stream()
                .map(OrganizationMembership::getOrganization)
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public OrganizationResponse updateOrganization(
            Long id,
            OrganizationRequest request) {

        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new OrganizationNotFoundException(id));

        User actingUser = currentUserProvider.requireCurrentUser();
        requireCanUpdate(actingUser, organization, id);

        if (!organization.getName().equals(request.name())
                && organizationRepository.existsByName(request.name())) {

            throw new DuplicateOrganizationNameException(request.name());
        }

        organization.setName(request.name());

        return mapToResponse(organization);
    }

    @Transactional
    public void deleteOrganization(Long id) {
        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new OrganizationNotFoundException(id));

        User actingUser = currentUserProvider.requireCurrentUser();
        requireCanDelete(actingUser, organization, id);

        organizationRepository.deleteById(id);
    }

    // --- authorization helpers ---

    private OrganizationRole roleOf(User user, Organization organization) {
        return membershipRepository.findByUserAndOrganization(user, organization)
                .map(OrganizationMembership::getRole)
                .orElse(null);
    }

    /** Any member can view. Non-members get 404 so existence is hidden. */
    private void requireCanView(User user, Organization organization, Long organizationId) {
        if (roleOf(user, organization) == null) {
            throw new OrganizationNotFoundException(organizationId);
        }
    }

    /** Visibility first, then ADMIN/OWNER. */
    private void requireCanUpdate(User user, Organization organization, Long organizationId) {
        OrganizationRole role = roleOf(user, organization);
        if (role == null) {
            throw new OrganizationNotFoundException(organizationId);
        }
        if (role != OrganizationRole.OWNER && role != OrganizationRole.ADMIN) {
            throw new InsufficientOrganizationPermissionException(
                    "Only an OWNER or ADMIN can update this organization.");
        }
    }

    /** Visibility first, then OWNER only. */
    private void requireCanDelete(User user, Organization organization, Long organizationId) {
        OrganizationRole role = roleOf(user, organization);
        if (role == null) {
            throw new OrganizationNotFoundException(organizationId);
        }
        if (role != OrganizationRole.OWNER) {
            throw new InsufficientOrganizationPermissionException(
                    "Only an OWNER can delete this organization.");
        }
    }

    private OrganizationResponse mapToResponse(
            Organization organization) {

        return new OrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getCreatedAt()
        );
    }
}

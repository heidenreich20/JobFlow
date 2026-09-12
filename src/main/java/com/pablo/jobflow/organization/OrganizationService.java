package com.pablo.jobflow.organization;

import com.pablo.jobflow.user.User;
import com.pablo.jobflow.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            OrganizationMembershipRepository membershipRepository,
            UserRepository userRepository) {

        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public OrganizationResponse createOrganization(OrganizationRequest request) {
        if (organizationRepository.existsByName(request.name())) {
            throw new DuplicateOrganizationNameException(request.name());
        }

        User authenticatedUser = getAuthenticatedUser();

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

        return mapToResponse(organization);
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> getAllOrganizations() {
        return organizationRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public OrganizationResponse updateOrganization(
            Long id,
            OrganizationRequest request) {

        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new OrganizationNotFoundException(id));

        if (!organization.getName().equals(request.name())
                && organizationRepository.existsByName(request.name())) {

            throw new DuplicateOrganizationNameException(request.name());
        }

        organization.setName(request.name());

        return mapToResponse(organization);
    }

    @Transactional
    public void deleteOrganization(Long id) {
        if (!organizationRepository.existsById(id)) {
            throw new OrganizationNotFoundException(id);
        }

        organizationRepository.deleteById(id);
    }

    private User getAuthenticatedUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String email = authentication.getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated user does not exist"
                        ));
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
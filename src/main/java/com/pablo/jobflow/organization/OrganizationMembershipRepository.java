package com.pablo.jobflow.organization;

import com.pablo.jobflow.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationMembershipRepository
        extends JpaRepository<OrganizationMembership, Long> {

    boolean existsByUserAndOrganization(User user, Organization organization);

    Optional<OrganizationMembership> findByUserAndOrganization(
            User user,
            Organization organization
    );

    List<OrganizationMembership> findByOrganization(
            Organization organization
    );

    List<OrganizationMembership> findByUser(
            User user
    );

    long countByOrganizationAndRole(
            Organization organization,
            OrganizationRole role
    );
}
package com.pablo.jobflow.organization;

import java.time.LocalDateTime;

public record OrganizationMembershipResponse(
        Long id,
        Long userId,
        String email,
        OrganizationRole role,
        LocalDateTime createdAt
) {}
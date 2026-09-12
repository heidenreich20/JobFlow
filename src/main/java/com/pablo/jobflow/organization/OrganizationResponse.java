package com.pablo.jobflow.organization;

import java.time.LocalDateTime;

public record OrganizationResponse(
    Long id,
    String name,
    LocalDateTime createdAt
) {}
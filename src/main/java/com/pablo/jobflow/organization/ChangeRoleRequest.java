package com.pablo.jobflow.organization;

import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(
        @NotNull(message = "Role is required")
        OrganizationRole role
) {}
package com.pablo.jobflow.organization;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddMemberRequest(

        @NotBlank(message = "Email cannot be empty or whitespace")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotNull(message = "Role is required")
        OrganizationRole role
) {}
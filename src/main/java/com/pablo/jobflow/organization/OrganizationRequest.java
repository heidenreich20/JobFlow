package com.pablo.jobflow.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrganizationRequest(
    @NotBlank(message = "Organization name cannot be empty or whitespace")
    @Size(max = 255, message = "Organization name must not exceed 255 characters")
    String name
) {}
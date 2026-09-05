package com.pablo.jobflow.task;

import jakarta.validation.constraints.NotNull;

public record TaskStatusRequest(

        @NotNull(message = "Status is required")
        TaskStatus status

) {}

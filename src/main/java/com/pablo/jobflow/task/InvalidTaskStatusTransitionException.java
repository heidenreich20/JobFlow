package com.pablo.jobflow.task;

public class InvalidTaskStatusTransitionException
        extends RuntimeException {

    public InvalidTaskStatusTransitionException(
            TaskStatus currentStatus,
            TaskStatus requestedStatus) {

        super(
                "Cannot change task status from "
                        + currentStatus
                        + " to "
                        + requestedStatus);
    }
}
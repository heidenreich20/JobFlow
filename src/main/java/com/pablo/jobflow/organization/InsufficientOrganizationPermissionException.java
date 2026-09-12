package com.pablo.jobflow.organization;

public class InsufficientOrganizationPermissionException extends RuntimeException {
    public InsufficientOrganizationPermissionException(String message) {
        super(message);
    }
}
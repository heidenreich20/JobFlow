package com.pablo.jobflow.organization;

public class OrganizationNotFoundException extends RuntimeException {
    public OrganizationNotFoundException(Long id) {
        super("Organization not found with ID: " + id);
    }
}
package com.pablo.jobflow.organization;

public class DuplicateOrganizationNameException extends RuntimeException {
    public DuplicateOrganizationNameException(String name) {
        super("An organization with the name '" + name + "' already exists.");
    }
}
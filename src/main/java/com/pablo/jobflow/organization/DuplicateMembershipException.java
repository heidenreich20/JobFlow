package com.pablo.jobflow.organization;

public class DuplicateMembershipException extends RuntimeException {
    public DuplicateMembershipException(String email, String organizationName) {
        super("User '" + email + "' is already a member of organization '" + organizationName + "'.");
    }
}
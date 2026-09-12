package com.pablo.jobflow.organization;

public class MembershipNotFoundException extends RuntimeException {
    public MembershipNotFoundException(Long userId, Long organizationId) {
        super("User " + userId + " is not a member of organization " + organizationId);
    }
}
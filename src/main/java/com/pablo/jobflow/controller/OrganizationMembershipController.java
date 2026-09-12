package com.pablo.jobflow.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.pablo.jobflow.organization.AddMemberRequest;
import com.pablo.jobflow.organization.ChangeRoleRequest;
import com.pablo.jobflow.organization.OrganizationMembershipResponse;
import com.pablo.jobflow.organization.OrganizationMembershipService;

@RestController
@RequestMapping("/api/organizations/{organizationId}/members")
public class OrganizationMembershipController {

    private final OrganizationMembershipService membershipService;

    public OrganizationMembershipController(OrganizationMembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @PostMapping
    public ResponseEntity<OrganizationMembershipResponse> addMember(
            @PathVariable Long organizationId,
            @Valid @RequestBody AddMemberRequest request) {

        OrganizationMembershipResponse response =
                membershipService.addMember(organizationId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<OrganizationMembershipResponse> changeRole(
            @PathVariable Long organizationId,
            @PathVariable Long userId,
            @Valid @RequestBody ChangeRoleRequest request) {

        OrganizationMembershipResponse response =
                membershipService.changeRole(organizationId, userId, request);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long organizationId,
            @PathVariable Long userId) {

        membershipService.removeMember(organizationId, userId);

        return ResponseEntity.noContent().build();
    }
}
package com.pablo.jobflow.project;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

import com.pablo.jobflow.organization.InsufficientOrganizationPermissionException;
import com.pablo.jobflow.organization.Organization;
import com.pablo.jobflow.organization.OrganizationMembershipRepository;
import com.pablo.jobflow.organization.OrganizationNotFoundException;
import com.pablo.jobflow.organization.OrganizationRepository;
import com.pablo.jobflow.task.TaskRepository;
import com.pablo.jobflow.user.User;
import com.pablo.jobflow.user.UserRepository;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    public ProjectService(
            ProjectRepository projectRepository,
            TaskRepository taskRepository,
            OrganizationRepository organizationRepository,
            OrganizationMembershipRepository membershipRepository,
            UserRepository userRepository) {

        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
    }

    public Project createProject(ProjectRequest request) {
        User actingUser = getAuthenticatedUser();

        Project project = new Project();
        project.setName(request.name());
        project.setDescription(request.description());

        if (request.organizationId() != null) {
            Organization organization = organizationRepository.findById(request.organizationId())
                    .orElseThrow(() -> new OrganizationNotFoundException(request.organizationId()));

            requireMembership(actingUser, organization);

            project.setOrganization(organization);
        } else {
            project.setOwner(actingUser);
        }

        return projectRepository.save(project);
    }

    public Project getProject(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ProjectNotFoundException(id));
    }

    public List<Project> getProjects() {
        return projectRepository.findAll();
    }

    public Project updateProject(Long id, ProjectRequest request) {
        Project existingProject = projectRepository.findById(id)
                .orElseThrow(() -> new ProjectNotFoundException(id));

        User actingUser = getAuthenticatedUser();

        if (existingProject.getOrganization() != null) {
            requireMembership(actingUser, existingProject.getOrganization());
        }

        Long targetOrganizationId = request.organizationId();

        if (targetOrganizationId != null) {
            Organization targetOrganization = organizationRepository.findById(targetOrganizationId)
                    .orElseThrow(() -> new OrganizationNotFoundException(targetOrganizationId));

            requireMembership(actingUser, targetOrganization);

            existingProject.setOrganization(targetOrganization);
            existingProject.setOwner(null);

        } else if (existingProject.getOrganization() != null) {
            existingProject.setOrganization(null);
            existingProject.setOwner(actingUser);
        }

        existingProject.setName(request.name());
        existingProject.setDescription(request.description());

        return projectRepository.save(existingProject);
    }

    public void deleteProject(Long id, boolean force) {
        if (!projectRepository.existsById(id)) {
            throw new ProjectNotFoundException(id);
        }

        boolean hasTasks = taskRepository.existsByProjectId(id);

        if (hasTasks && !force) {
            throw new ProjectHasTasksException(id);
        }

        if (force) {
            taskRepository.deleteByProjectId(id);
        }

        projectRepository.deleteById(id);
    }

    private void requireMembership(User user, Organization organization) {
        if (!membershipRepository.existsByUserAndOrganization(user, organization)) {
            throw new InsufficientOrganizationPermissionException(
                    "You must be a member of organization '" + organization.getName()
                            + "' to perform this action.");
        }
    }

    private User getAuthenticatedUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String email = authentication.getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user does not exist"));
    }
}
package com.pablo.jobflow.project;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

import com.pablo.jobflow.organization.InsufficientOrganizationPermissionException;
import com.pablo.jobflow.organization.Organization;
import com.pablo.jobflow.organization.OrganizationMembership;
import com.pablo.jobflow.organization.OrganizationMembershipRepository;
import com.pablo.jobflow.organization.OrganizationNotFoundException;
import com.pablo.jobflow.organization.OrganizationRepository;
import com.pablo.jobflow.organization.OrganizationRole;
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

            requireCanCreateUnder(actingUser, organization);

            project.setOrganization(organization);
        } else {
            project.setOwner(actingUser);
        }

        return projectRepository.save(project);
    }

    public Project getProject(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ProjectNotFoundException(id));

        User actingUser = getAuthenticatedUser();
        requireCanView(actingUser, project, id);

        return project;
    }

    public List<Project> getProjects() {
        User actingUser = getAuthenticatedUser();

        return projectRepository.findAll().stream()
                .filter(project -> canView(actingUser, project))
                .toList();
    }

    public Project updateProject(Long id, ProjectRequest request) {
        Project existingProject = projectRepository.findById(id)
                .orElseThrow(() -> new ProjectNotFoundException(id));

        User actingUser = getAuthenticatedUser();

        requireCanEditFields(actingUser, existingProject, id);

        Long targetOrganizationId = request.organizationId();
        Long currentOrganizationId = existingProject.getOrganization() != null
                ? existingProject.getOrganization().getId()
                : null;

        boolean isReassigning = !Objects.equals(targetOrganizationId, currentOrganizationId);

        if (isReassigning) {
            requireCanManage(actingUser, existingProject, id);

            if (targetOrganizationId != null) {
                Organization targetOrganization = organizationRepository.findById(targetOrganizationId)
                        .orElseThrow(() -> new OrganizationNotFoundException(targetOrganizationId));

                requireCanManageOrganization(actingUser, targetOrganization);

                existingProject.setOrganization(targetOrganization);
                existingProject.setOwner(null);
            } else {
                existingProject.setOrganization(null);
                existingProject.setOwner(actingUser);
            }
        }

        existingProject.setName(request.name());
        existingProject.setDescription(request.description());

        return projectRepository.save(existingProject);
    }

    public void deleteProject(Long id, boolean force) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ProjectNotFoundException(id));

        User actingUser = getAuthenticatedUser();
        requireCanManage(actingUser, project, id);

        boolean hasTasks = taskRepository.existsByProjectId(id);

        if (hasTasks && !force) {
            throw new ProjectHasTasksException(id);
        }

        if (force) {
            taskRepository.deleteByProjectId(id);
        }

        projectRepository.deleteById(id);
    }

    // --- authorization helpers ---

    private boolean isOwner(User user, Project project) {
        return project.getOwner() != null && project.getOwner().getEmail().equals(user.getEmail());
    }

    private OrganizationRole roleOf(User user, Organization organization) {
        return membershipRepository.findByUserAndOrganization(user, organization)
                .map(OrganizationMembership::getRole)
                .orElse(null);
    }

    private boolean isOwnerOrAdmin(OrganizationRole role) {
        return role == OrganizationRole.OWNER || role == OrganizationRole.ADMIN;
    }

    private boolean canView(User user, Project project) {
        if (project.getOwner() != null) {
            return isOwner(user, project);
        }
        return roleOf(user, project.getOrganization()) != null;
    }

    private void requireCanView(User user, Project project, Long projectId) {
        if (!canView(user, project)) {
            throw new ProjectNotFoundException(projectId);
        }
    }

    /** Can edit name/description: owner (personal) or any member except VIEWER (organization). */
    private void requireCanEditFields(User user, Project project, Long projectId) {
        if (project.getOwner() != null) {
            if (!isOwner(user, project)) {
                throw new ProjectNotFoundException(projectId);
            }
            return;
        }

        OrganizationRole role = roleOf(user, project.getOrganization());
        if (role == null) {
            throw new ProjectNotFoundException(projectId);
        }
        if (role == OrganizationRole.VIEWER) {
            throw new InsufficientOrganizationPermissionException(
                    "VIEWER cannot edit projects.");
        }
    }

    /** Can reassign or delete: owner (personal) or OWNER/ADMIN (organization). */
    private void requireCanManage(User user, Project project, Long projectId) {
        if (project.getOwner() != null) {
            if (!isOwner(user, project)) {
                throw new ProjectNotFoundException(projectId);
            }
            return;
        }

        OrganizationRole role = roleOf(user, project.getOrganization());
        if (role == null) {
            throw new ProjectNotFoundException(projectId);
        }
        if (!isOwnerOrAdmin(role)) {
            throw new InsufficientOrganizationPermissionException(
                    "Only an OWNER or ADMIN can perform this action.");
        }
    }

    /** Can move a project INTO this organization: must be OWNER/ADMIN there. */
    private void requireCanManageOrganization(User user, Organization organization) {
        OrganizationRole role = roleOf(user, organization);
        if (!isOwnerOrAdmin(role)) {
            throw new InsufficientOrganizationPermissionException(
                    "Only an OWNER or ADMIN of organization '" + organization.getName()
                            + "' can move a project there.");
        }
    }

    /** Can create a project under this organization: any member except VIEWER. */
    private void requireCanCreateUnder(User user, Organization organization) {
        OrganizationRole role = roleOf(user, organization);
        if (role == null) {
            throw new InsufficientOrganizationPermissionException(
                    "You must be a member of organization '" + organization.getName()
                            + "' to create a project there.");
        }
        if (role == OrganizationRole.VIEWER) {
            throw new InsufficientOrganizationPermissionException(
                    "VIEWER cannot create projects.");
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
package com.pablo.jobflow.project;

import org.springframework.stereotype.Service;

import java.util.List;

import com.pablo.jobflow.task.TaskRepository;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;

    public ProjectService(ProjectRepository projectRepository, TaskRepository taskRepository) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
    }

    public Project createProject(ProjectRequest request) {
        Project project = new Project();
        project.setName(request.name());
        project.setDescription(request.description());
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
}
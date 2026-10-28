package com.agentprobe.project;

import com.agentprobe.api.ApiException;
import com.agentprobe.project.ProjectDtos.ProjectRequest;
import com.agentprobe.project.ProjectDtos.ProjectResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projects;

    public ProjectService(ProjectRepository projects) {
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(UUID ownerId) {
        return projects.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(UUID id, UUID ownerId) {
        return ProjectResponse.from(findOwned(id, ownerId));
    }

    @Transactional
    public ProjectResponse create(UUID ownerId, ProjectRequest req) {
        String name = req.name().trim();
        if (projects.existsByOwnerIdAndName(ownerId, name)) {
            throw duplicate();
        }
        Project saved = projects.save(new Project(ownerId, name, req.description()));
        return ProjectResponse.from(saved);
    }

    @Transactional
    public ProjectResponse update(UUID id, UUID ownerId, ProjectRequest req) {
        Project project = findOwned(id, ownerId);
        String name = req.name().trim();
        if (projects.existsByOwnerIdAndNameAndIdNot(ownerId, name, id)) {
            throw duplicate();
        }
        project.setName(name);
        project.setDescription(req.description());
        return ProjectResponse.from(project);
    }

    @Transactional
    public void delete(UUID id, UUID ownerId) {
        projects.delete(findOwned(id, ownerId));
    }

    /**
     * The ownership check lives here. A project that exists but belongs to someone
     * else is reported as 404, the same as one that does not exist.
     */
    private Project findOwned(UUID id, UUID ownerId) {
        return projects.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private static ApiException duplicate() {
        return new ApiException(HttpStatus.CONFLICT, "You already have a project with this name");
    }
}
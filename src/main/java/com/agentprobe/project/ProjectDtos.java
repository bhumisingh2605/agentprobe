package com.agentprobe.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record ProjectRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2000) String description) {
    }

    public record ProjectResponse(
            UUID id,
            String name,
            String description,
            Instant createdAt) {

        public static ProjectResponse from(Project p) {
            return new ProjectResponse(p.getId(), p.getName(), p.getDescription(), p.getCreatedAt());
        }
    }
}
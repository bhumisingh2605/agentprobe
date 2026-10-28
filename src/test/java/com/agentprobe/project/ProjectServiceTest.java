package com.agentprobe.project;

import com.agentprobe.api.ApiException;
import com.agentprobe.project.ProjectDtos.ProjectRequest;
import com.agentprobe.project.ProjectDtos.ProjectResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    ProjectRepository repo;

    @InjectMocks
    ProjectService service;

    private final UUID owner = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    @Test
    void createSavesProjectForTheCaller() {
        when(repo.existsByOwnerIdAndName(owner, "Support Bot")).thenReturn(false);
        when(repo.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectResponse res = service.create(owner, new ProjectRequest("  Support Bot  ", "desc"));

        assertThat(res.name()).isEqualTo("Support Bot");
        assertThat(res.description()).isEqualTo("desc");
    }

    @Test
    void createRejectsDuplicateNameForSameOwner() {
        when(repo.existsByOwnerIdAndName(owner, "Support Bot")).thenReturn(true);

        assertThatThrownBy(() -> service.create(owner, new ProjectRequest("Support Bot", null)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);
        verify(repo, never()).save(any());
    }

    @Test
    void getOfSomeoneElsesProjectIsNotFound() {
        UUID id = UUID.randomUUID();
        when(repo.findByIdAndOwnerId(id, stranger)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id, stranger))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    void updateOfSomeoneElsesProjectIsNotFound() {
        UUID id = UUID.randomUUID();
        when(repo.findByIdAndOwnerId(id, stranger)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, stranger, new ProjectRequest("X", null)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteOfSomeoneElsesProjectIsNotFoundAndDeletesNothing() {
        UUID id = UUID.randomUUID();
        when(repo.findByIdAndOwnerId(id, stranger)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id, stranger))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
        verify(repo, never()).delete(any(Project.class));
    }

    @Test
    void updateRejectsNameUsedByAnotherProjectOfTheSameOwner() {
        UUID id = UUID.randomUUID();
        Project existing = new Project(owner, "Old", null);
        when(repo.findByIdAndOwnerId(id, owner)).thenReturn(Optional.of(existing));
        when(repo.existsByOwnerIdAndNameAndIdNot(owner, "Taken", id)).thenReturn(true);

        assertThatThrownBy(() -> service.update(id, owner, new ProjectRequest("Taken", null)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);
    }

    @Test
    void listOnlyAsksForTheCallersProjects() {
        when(repo.findAllByOwnerIdOrderByCreatedAtDesc(owner))
                .thenReturn(List.of(new Project(owner, "A", null)));

        assertThat(service.list(owner)).hasSize(1);
        verify(repo, never()).findAll();
    }
}
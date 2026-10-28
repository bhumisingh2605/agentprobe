package com.agentprobe.agent;

import com.agentprobe.agent.AgentDtos.AgentRequest;
import com.agentprobe.agent.AgentDtos.VersionRequest;
import com.agentprobe.agent.AgentDtos.VersionResponse;
import com.agentprobe.api.ApiException;
import com.agentprobe.crypto.SecretCipher;
import com.agentprobe.project.Project;
import com.agentprobe.project.ProjectRepository;
import com.agentprobe.security.SsrfGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock ProjectRepository projects;
    @Mock AgentRepository agents;
    @Mock AgentVersionRepository versions;
    @Mock SecretCipher cipher;
    @Mock SsrfGuard ssrfGuard;

    @InjectMocks AgentService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID agentId = UUID.randomUUID();

    private void ownProject() {
        when(projects.findByIdAndOwnerId(projectId, userId))
                .thenReturn(Optional.of(new Project(userId, "p", null)));
    }

    private void agentExists() {
        when(agents.findByIdAndProjectId(agentId, projectId))
                .thenReturn(Optional.of(new Agent(projectId, "a", null)));
    }

    private ApiException thrownBy(Runnable r) {
        try {
            r.run();
        } catch (ApiException e) {
            return e;
        }
        throw new AssertionError("Expected ApiException");
    }

    @Test
    void foreignProjectIsReportedAsNotFound() {
        when(projects.findByIdAndOwnerId(projectId, userId)).thenReturn(Optional.empty());

        ApiException e = thrownBy(() -> service.listAgents(projectId, userId));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(agents, never()).findAllByProjectIdOrderByCreatedAtDesc(any());
    }

    @Test
    void duplicateAgentNameIsConflict() {
        ownProject();
        when(agents.existsByProjectIdAndName(projectId, "ShopBot")).thenReturn(true);

        ApiException e = thrownBy(() -> service.createAgent(projectId, userId, new AgentRequest("ShopBot", null)));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(agents, never()).save(any());
    }

    @Test
    void secretIsEncryptedAndNeverReturned() {
        ownProject();
        agentExists();
        when(versions.existsByAgentIdAndVersionLabel(agentId, "v1")).thenReturn(false);
        when(cipher.encrypt("secret")).thenReturn("ENC");
        when(versions.save(any(AgentVersion.class))).thenAnswer(i -> i.getArgument(0));

        VersionRequest req = new VersionRequest("v1", "http://localhost:8082/chat", "X-Api-Key", "secret",
                null, null, "llama3.1", null, null);
        VersionResponse res = service.createVersion(projectId, agentId, userId, req);

        assertThat(res.hasAuthSecret()).isTrue();
        ArgumentCaptor<AgentVersion> captor = ArgumentCaptor.forClass(AgentVersion.class);
        verify(versions).save(captor.capture());
        assertThat(captor.getValue().getAuthHeaderValueEnc()).isEqualTo("ENC");
        assertThat(captor.getValue().getRequestTemplate()).contains("{{message}}");
    }

    @Test
    void headerNameWithoutValueIsRejected() {
        ownProject();
        agentExists();
        when(versions.existsByAgentIdAndVersionLabel(agentId, "v1")).thenReturn(false);

        VersionRequest req = new VersionRequest("v1", "http://localhost:8082/chat", "X-Api-Key", null,
                null, null, null, null, null);
        ApiException e = thrownBy(() -> service.createVersion(projectId, agentId, userId, req));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void templateWithoutMessagePlaceholderIsRejected() {
        ownProject();
        agentExists();
        when(versions.existsByAgentIdAndVersionLabel(agentId, "v1")).thenReturn(false);

        VersionRequest req = new VersionRequest("v1", "http://localhost:8082/chat", null, null,
                Map.of("foo", "bar"), null, null, null, null);
        ApiException e = thrownBy(() -> service.createVersion(projectId, agentId, userId, req));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
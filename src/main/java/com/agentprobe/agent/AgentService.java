package com.agentprobe.agent;

import com.agentprobe.adapter.AgentTarget;
import com.agentprobe.agent.AgentDtos.AgentRequest;
import com.agentprobe.agent.AgentDtos.AgentResponse;
import com.agentprobe.agent.AgentDtos.VersionRequest;
import com.agentprobe.agent.AgentDtos.VersionResponse;
import com.agentprobe.api.ApiException;
import com.agentprobe.crypto.SecretCipher;
import com.agentprobe.project.ProjectRepository;
import com.agentprobe.security.SsrfGuard;
import net.minidev.json.JSONValue;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AgentService {

    static final Map<String, Object> DEFAULT_TEMPLATE = Map.of(
            "message", "{{message}}", "sessionId", "{{sessionId}}");
    static final Map<String, Object> DEFAULT_MAPPING = Map.of(
            "answerPath", "$.answer", "toolCallsPath", "$.toolCalls");

    private final ProjectRepository projects;
    private final AgentRepository agents;
    private final AgentVersionRepository versions;
    private final SecretCipher cipher;
    private final SsrfGuard ssrfGuard;

    public AgentService(ProjectRepository projects, AgentRepository agents, AgentVersionRepository versions,
                        SecretCipher cipher, SsrfGuard ssrfGuard) {
        this.projects = projects;
        this.agents = agents;
        this.versions = versions;
        this.cipher = cipher;
        this.ssrfGuard = ssrfGuard;
    }

    // ---------- agents ----------

    @Transactional(readOnly = true)
    public List<AgentResponse> listAgents(UUID projectId, UUID userId) {
        requireProject(projectId, userId);
        return agents.findAllByProjectIdOrderByCreatedAtDesc(projectId).stream().map(AgentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AgentResponse getAgent(UUID projectId, UUID agentId, UUID userId) {
        requireProject(projectId, userId);
        return AgentResponse.from(findAgent(projectId, agentId));
    }

    @Transactional
    public AgentResponse createAgent(UUID projectId, UUID userId, AgentRequest req) {
        requireProject(projectId, userId);
        String name = req.name().trim();
        if (agents.existsByProjectIdAndName(projectId, name)) {
            throw conflict("This project already has an agent with this name");
        }
        return AgentResponse.from(agents.save(new Agent(projectId, name, req.description())));
    }

    @Transactional
    public AgentResponse updateAgent(UUID projectId, UUID agentId, UUID userId, AgentRequest req) {
        requireProject(projectId, userId);
        Agent agent = findAgent(projectId, agentId);
        String name = req.name().trim();
        if (agents.existsByProjectIdAndNameAndIdNot(projectId, name, agentId)) {
            throw conflict("This project already has an agent with this name");
        }
        agent.setName(name);
        agent.setDescription(req.description());
        return AgentResponse.from(agent);
    }

    @Transactional
    public void deleteAgent(UUID projectId, UUID agentId, UUID userId) {
        requireProject(projectId, userId);
        agents.delete(findAgent(projectId, agentId));
    }

    // ---------- versions ----------

    @Transactional(readOnly = true)
    public List<VersionResponse> listVersions(UUID projectId, UUID agentId, UUID userId) {
        requireProject(projectId, userId);
        findAgent(projectId, agentId);
        return versions.findAllByAgentIdOrderByCreatedAtDesc(agentId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public VersionResponse getVersion(UUID projectId, UUID agentId, UUID versionId, UUID userId) {
        return toResponse(findVersion(projectId, agentId, versionId, userId));
    }

    @Transactional
    public VersionResponse createVersion(UUID projectId, UUID agentId, UUID userId, VersionRequest req) {
        requireProject(projectId, userId);
        findAgent(projectId, agentId);

        String label = req.versionLabel().trim();
        if (versions.existsByAgentIdAndVersionLabel(agentId, label)) {
            throw conflict("This agent already has a version with this label");
        }
        String url = req.endpointUrl().trim();
        ssrfGuard.validate(url);

        boolean hasName = req.authHeaderName() != null && !req.authHeaderName().isBlank();
        boolean hasValue = req.authHeaderValue() != null && !req.authHeaderValue().isBlank();
        if (hasName != hasValue) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Provide both authHeaderName and authHeaderValue, or neither");
        }

        Map<String, Object> template = req.requestTemplate() == null || req.requestTemplate().isEmpty()
                ? DEFAULT_TEMPLATE : req.requestTemplate();
        String templateJson = JSONValue.toJSONString(template);
        if (!templateJson.contains("{{message}}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "requestTemplate must contain the placeholder {{message}}");
        }
        Map<String, Object> mapping = req.responseMapping() == null || req.responseMapping().isEmpty()
                ? DEFAULT_MAPPING : req.responseMapping();

        AgentVersion v = new AgentVersion(agentId, label, url);
        if (hasName) {
            v.setAuthHeaderName(req.authHeaderName().trim());
            v.setAuthHeaderValueEnc(cipher.encrypt(req.authHeaderValue()));
        }
        v.setRequestTemplate(templateJson);
        v.setResponseMapping(JSONValue.toJSONString(mapping));
        v.setModel(req.model());
        v.setPromptHash(req.promptHash());
        v.setLabels(JSONValue.toJSONString(req.labels() == null ? Map.of() : req.labels()));
        return toResponse(versions.save(v));
    }

    @Transactional
    public void deleteVersion(UUID projectId, UUID agentId, UUID versionId, UUID userId) {
        versions.delete(findVersion(projectId, agentId, versionId, userId));
    }

    /** Used by the playground / run engine. Decrypts the secret in memory only. */
    @Transactional(readOnly = true)
    public AgentTarget resolveTarget(UUID projectId, UUID agentId, UUID versionId, UUID userId) {
        AgentVersion v = findVersion(projectId, agentId, versionId, userId);
        String secret = v.getAuthHeaderValueEnc() == null ? null : cipher.decrypt(v.getAuthHeaderValueEnc());
        return new AgentTarget(v.getEndpointUrl(), v.getAuthHeaderName(), secret,
                v.getRequestTemplate(), v.getResponseMapping());
    }

    // ---------- helpers ----------

    /** A project owned by someone else is reported as 404, same as a missing one. */
    private void requireProject(UUID projectId, UUID userId) {
        projects.findByIdAndOwnerId(projectId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private Agent findAgent(UUID projectId, UUID agentId) {
        return agents.findByIdAndProjectId(agentId, projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Agent not found"));
    }

    private AgentVersion findVersion(UUID projectId, UUID agentId, UUID versionId, UUID userId) {
        requireProject(projectId, userId);
        findAgent(projectId, agentId);
        return versions.findByIdAndAgentId(versionId, agentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Agent version not found"));
    }

    private static ApiException conflict(String msg) {
        return new ApiException(HttpStatus.CONFLICT, msg);
    }

    private VersionResponse toResponse(AgentVersion v) {
        return new VersionResponse(v.getId(), v.getAgentId(), v.getVersionLabel(), v.getEndpointUrl(),
                v.getAuthHeaderName(), v.getAuthHeaderValueEnc() != null,
                parse(v.getRequestTemplate()), parse(v.getResponseMapping()),
                v.getModel(), v.getPromptHash(), parse(v.getLabels()), v.getCreatedAt());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String json) {
        Object o = JSONValue.parse(json);
        return o instanceof Map<?, ?> m ? new LinkedHashMap<>((Map<String, Object>) m) : Map.of();
    }
}
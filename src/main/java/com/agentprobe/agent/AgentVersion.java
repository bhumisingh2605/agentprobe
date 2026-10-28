package com.agentprobe.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;
import java.util.UUID;

/** JSON columns are kept as raw JSON strings; "?::jsonb" casts text to jsonb on write. */
@Entity
@Table(name = "agent_version")
public class AgentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agent_id", nullable = false, updatable = false)
    private UUID agentId;

    @Column(name = "version_label", nullable = false, length = 60)
    private String versionLabel;

    @Column(name = "endpoint_url", nullable = false, length = 2048)
    private String endpointUrl;

    @Column(name = "auth_header_name", length = 120)
    private String authHeaderName;

    /** AES-GCM encrypted. Never returned by the API. */
    @Column(name = "auth_header_value_enc", columnDefinition = "text")
    private String authHeaderValueEnc;

    @Column(name = "request_template", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String requestTemplate = "{}";

    @Column(name = "response_mapping", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String responseMapping = "{}";

    @Column(length = 120)
    private String model;

    @Column(name = "prompt_hash", length = 128)
    private String promptHash;

    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String labels = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AgentVersion() {
    }

    public AgentVersion(UUID agentId, String versionLabel, String endpointUrl) {
        this.agentId = agentId;
        this.versionLabel = versionLabel;
        this.endpointUrl = endpointUrl;
    }

    public UUID getId() { return id; }
    public UUID getAgentId() { return agentId; }
    public String getVersionLabel() { return versionLabel; }
    public String getEndpointUrl() { return endpointUrl; }
    public String getAuthHeaderName() { return authHeaderName; }
    public void setAuthHeaderName(String v) { this.authHeaderName = v; }
    public String getAuthHeaderValueEnc() { return authHeaderValueEnc; }
    public void setAuthHeaderValueEnc(String v) { this.authHeaderValueEnc = v; }
    public String getRequestTemplate() { return requestTemplate; }
    public void setRequestTemplate(String v) { this.requestTemplate = v; }
    public String getResponseMapping() { return responseMapping; }
    public void setResponseMapping(String v) { this.responseMapping = v; }
    public String getModel() { return model; }
    public void setModel(String v) { this.model = v; }
    public String getPromptHash() { return promptHash; }
    public void setPromptHash(String v) { this.promptHash = v; }
    public String getLabels() { return labels; }
    public void setLabels(String v) { this.labels = v; }
    public Instant getCreatedAt() { return createdAt; }
}
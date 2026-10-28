package com.agentprobe.agent;

import com.agentprobe.project.Project;
import com.agentprobe.project.ProjectRepository;
import com.agentprobe.user.User;
import com.agentprobe.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional // every test rolls back, so the dev database stays clean
class AgentPersistenceTest {

    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired AgentRepository agents;
    @Autowired AgentVersionRepository versions;
    @PersistenceContext EntityManager em;

    @Test
    void jsonbColumnsRoundTripAndOwnershipQueriesWork() {
        User u = users.save(new User("persist-" + UUID.randomUUID() + "@test.com", "hash"));
        Project p = projects.save(new Project(u.getId(), "p-" + UUID.randomUUID(), null));
        Agent a = agents.save(new Agent(p.getId(), "ShopBot", null));

        AgentVersion v = new AgentVersion(a.getId(), "v1", "http://localhost:8082/chat");
        v.setRequestTemplate("{\"message\":\"{{message}}\"}");
        v.setResponseMapping("{\"answerPath\":\"$.answer\"}");
        v.setLabels("{\"promptVersion\":\"v1\"}");
        UUID id = versions.save(v).getId();

        em.flush();
        em.clear();

        AgentVersion loaded = versions.findById(id).orElseThrow();
        assertThat(loaded.getRequestTemplate()).contains("{{message}}");
        assertThat(loaded.getResponseMapping()).contains("answerPath");
        assertThat(loaded.getLabels()).contains("promptVersion");

        assertThat(agents.findByIdAndProjectId(a.getId(), p.getId())).isPresent();
        assertThat(agents.findByIdAndProjectId(a.getId(), UUID.randomUUID())).isEmpty();
        assertThat(versions.existsByAgentIdAndVersionLabel(a.getId(), "v1")).isTrue();
    }
}
package com.agentprobe.demo;

import com.agentprobe.agent.AgentDtos.AgentRequest;
import com.agentprobe.agent.AgentDtos.AgentResponse;
import com.agentprobe.agent.AgentDtos.VersionRequest;
import com.agentprobe.agent.AgentService;
import com.agentprobe.project.ProjectDtos.ProjectRequest;
import com.agentprobe.project.ProjectDtos.ProjectResponse;
import com.agentprobe.project.ProjectService;
import com.agentprobe.suite.SuiteService;
import com.agentprobe.user.User;
import com.agentprobe.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Creates a demo user, project, agent and suite on first start. Active only with the "demo" profile. */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final ProjectService projects;
    private final AgentService agents;
    private final SuiteService suites;
    private final String email;
    private final String password;
    private final String shopbotUrl;

    public DemoDataSeeder(UserRepository users, PasswordEncoder encoder, ProjectService projects,
                          AgentService agents, SuiteService suites,
                          @Value("${agentprobe.demo.email:demo@agentprobe.dev}") String email,
                          @Value("${agentprobe.demo.password:demo-password-123}") String password,
                          @Value("${agentprobe.demo.shopbot-url:http://localhost:8082/chat}") String shopbotUrl) {
        this.users = users;
        this.encoder = encoder;
        this.projects = projects;
        this.agents = agents;
        this.suites = suites;
        this.email = email;
        this.password = password;
        this.shopbotUrl = shopbotUrl;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (users.findByEmail(email).isPresent()) {
                return;
            }
            User user = users.save(new User(email, encoder.encode(password)));
            ProjectResponse project = projects.create(user.getId(),
                    new ProjectRequest("ShopBot demo", "Seeded demo project"));
            AgentResponse agent = agents.createAgent(project.id(), user.getId(),
                    new AgentRequest("ShopBot", "Sample e-commerce support agent"));
            agents.createVersion(project.id(), agent.id(), user.getId(), new VersionRequest(
                    "prompt-v1", shopbotUrl, null, null, null, null, "llama3.2", null,
                    Map.of("promptVersion", "v1")));
            for (String file : new String[]{"demo/shopbot-regression.yaml", "demo/shopbot-safety.yaml"}) {
                String yaml = new ClassPathResource(file).getContentAsString(StandardCharsets.UTF_8);
                suites.importYaml(project.id(), user.getId(), yaml);
            }
            log.info("Demo data created. Sign in as {}", email);
        } catch (Exception e) {
            log.warn("Could not seed demo data: {}", e.getMessage());
        }
    }
}
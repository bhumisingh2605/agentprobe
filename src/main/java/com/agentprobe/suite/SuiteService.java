package com.agentprobe.suite;

import com.agentprobe.api.ApiException;
import com.agentprobe.evaluation.Evaluator;
import com.agentprobe.evaluation.EvaluatorRegistry;
import com.agentprobe.project.ProjectRepository;
import com.agentprobe.suite.ScenarioYaml.ParsedSuite;
import com.agentprobe.suite.SuiteDtos.ImportResult;
import com.agentprobe.suite.SuiteDtos.ScenarioRequest;
import com.agentprobe.suite.SuiteDtos.ScenarioResponse;
import com.agentprobe.suite.SuiteDtos.SuiteRequest;
import com.agentprobe.suite.SuiteDtos.SuiteResponse;
import com.agentprobe.util.Json;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class SuiteService {

    private static final Pattern EXTERNAL_ID = Pattern.compile("^[A-Za-z0-9._-]{1,120}$");

    private final ProjectRepository projects;
    private final TestSuiteRepository suites;
    private final ScenarioRepository scenarios;
    private final EvaluatorRegistry registry;
    private final ScenarioYaml yaml;

    public SuiteService(ProjectRepository projects, TestSuiteRepository suites, ScenarioRepository scenarios,
                        EvaluatorRegistry registry, ScenarioYaml yaml) {
        this.projects = projects;
        this.suites = suites;
        this.scenarios = scenarios;
        this.registry = registry;
        this.yaml = yaml;
    }

    // ------------------------------------------------------------------ suites

    @Transactional(readOnly = true)
    public List<SuiteResponse> listSuites(UUID projectId, UUID userId) {
        requireProject(projectId, userId);
        List<SuiteResponse> out = new ArrayList<SuiteResponse>();
        for (TestSuite s : suites.findAllByProjectIdOrderByCreatedAtDesc(projectId)) {
            out.add(toResponse(s));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public SuiteResponse getSuite(UUID projectId, UUID suiteId, UUID userId) {
        return toResponse(requireSuite(projectId, suiteId, userId));
    }

    @Transactional
    public SuiteResponse createSuite(UUID projectId, UUID userId, SuiteRequest req) {
        requireProject(projectId, userId);
        String name = req.name().trim();
        if (suites.existsByProjectIdAndName(projectId, name)) {
            throw new ApiException(HttpStatus.CONFLICT, "This project already has a suite with this name");
        }
        return toResponse(suites.save(new TestSuite(projectId, name, req.description())));
    }

    @Transactional
    public void deleteSuite(UUID projectId, UUID suiteId, UUID userId) {
        suites.delete(requireSuite(projectId, suiteId, userId));
    }

    // --------------------------------------------------------------- scenarios

    @Transactional(readOnly = true)
    public List<ScenarioResponse> listScenarios(UUID projectId, UUID suiteId, UUID userId) {
        TestSuite suite = requireSuite(projectId, suiteId, userId);
        List<ScenarioResponse> out = new ArrayList<ScenarioResponse>();
        for (Scenario s : scenarios.findAllBySuiteIdOrderByExternalIdAsc(suite.getId())) {
            out.add(toResponse(s));
        }
        return out;
    }

    @Transactional
    public ScenarioResponse createScenario(UUID projectId, UUID suiteId, UUID userId, ScenarioRequest req) {
        TestSuite suite = requireSuite(projectId, suiteId, userId);
        validate(req);
        if (scenarios.existsBySuiteIdAndExternalId(suite.getId(), req.externalId())) {
            throw new ApiException(HttpStatus.CONFLICT, "This suite already has a scenario with this id");
        }
        Scenario s = new Scenario(suite.getId(), req.externalId(), normalizedType(req));
        apply(s, req);
        return toResponse(scenarios.save(s));
    }

    @Transactional
    public ScenarioResponse updateScenario(UUID projectId, UUID suiteId, UUID scenarioId, UUID userId,
                                           ScenarioRequest req) {
        TestSuite suite = requireSuite(projectId, suiteId, userId);
        Scenario s = scenarios.findByIdAndSuiteId(scenarioId, suite.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Scenario not found"));
        validate(req);
        if (!s.getExternalId().equals(req.externalId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The scenario id cannot be changed");
        }
        s.setType(normalizedType(req));
        apply(s, req);
        return toResponse(s);
    }

    @Transactional
    public void deleteScenario(UUID projectId, UUID suiteId, UUID scenarioId, UUID userId) {
        TestSuite suite = requireSuite(projectId, suiteId, userId);
        Scenario s = scenarios.findByIdAndSuiteId(scenarioId, suite.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Scenario not found"));
        scenarios.delete(s);
    }

    // -------------------------------------------------------------- yaml in/out

    @Transactional
    public ImportResult importYaml(UUID projectId, UUID userId, String body) {
        requireProject(projectId, userId);
        if (body == null || body.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Request body is empty");
        }
        ParsedSuite parsed = yaml.parse(body);

        Set<String> seen = new HashSet<String>();
        for (ScenarioRequest r : parsed.scenarios()) {
            validate(r);
            if (!seen.add(r.externalId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Duplicate scenario id '" + r.externalId() + "'");
            }
        }

        TestSuite suite;
        Optional<TestSuite> existing = suites.findByProjectIdAndName(projectId, parsed.name());
        if (existing.isPresent()) {
            suite = existing.get();
            if (parsed.description() != null) {
                suite.setDescription(parsed.description());
            }
        } else {
            suite = suites.save(new TestSuite(projectId, parsed.name(), parsed.description()));
        }

        int created = 0;
        int updated = 0;
        for (ScenarioRequest r : parsed.scenarios()) {
            Optional<Scenario> found = scenarios.findBySuiteIdAndExternalId(suite.getId(), r.externalId());
            if (found.isPresent()) {
                Scenario s = found.get();
                s.setType(normalizedType(r));
                apply(s, r);
                updated++;
            } else {
                Scenario s = new Scenario(suite.getId(), r.externalId(), normalizedType(r));
                apply(s, r);
                scenarios.save(s);
                created++;
            }
        }
        return new ImportResult(suite.getId(), suite.getName(), created, updated);
    }

    @Transactional(readOnly = true)
    public String exportYaml(UUID projectId, UUID suiteId, UUID userId) {
        TestSuite suite = requireSuite(projectId, suiteId, userId);
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("suite", suite.getName());
        if (suite.getDescription() != null) {
            root.put("description", suite.getDescription());
        }
        List<Object> list = new ArrayList<Object>();
        for (Scenario s : scenarios.findAllBySuiteIdOrderByExternalIdAsc(suite.getId())) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", s.getExternalId());
            m.put("type", s.getType());
            if (s.getInput() != null) m.put("input", s.getInput());
            if (s.getPersona() != null) m.put("persona", s.getPersona());
            if (s.getGoal() != null) m.put("goal", s.getGoal());
            if (s.getMaxTurns() != null) m.put("max_turns", s.getMaxTurns());
            m.put("tags", Json.list(s.getTags()));
            m.put("evaluators", Json.list(s.getEvaluators()));
            list.add(m);
        }
        root.put("scenarios", list);
        return yaml.dump(root);
    }

    // ------------------------------------------------ used by the run engine

    @Transactional(readOnly = true)
    public List<Scenario> scenariosForRun(UUID projectId, UUID suiteId, UUID userId) {
        TestSuite suite = requireSuite(projectId, suiteId, userId);
        return scenarios.findAllBySuiteIdOrderByExternalIdAsc(suite.getId());
    }

    /** A project owned by someone else is reported as 404, same as a missing one. */
    public void requireProject(UUID projectId, UUID userId) {
        projects.findByIdAndOwnerId(projectId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    public TestSuite requireSuite(UUID projectId, UUID suiteId, UUID userId) {
        requireProject(projectId, userId);
        return suites.findByIdAndProjectId(suiteId, projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Suite not found"));
    }

    // ------------------------------------------------------------------ helpers

    private void validate(ScenarioRequest r) {
        String id = r.externalId();
        if (id == null || !EXTERNAL_ID.matcher(id).matches()) {
            throw bad("Scenario id must use letters, digits, dot, dash or underscore (max 120 characters)");
        }
        String type = normalizedType(r);
        if (!type.equals("single_turn") && !type.equals("multi_turn")) {
            throw bad("Scenario '" + id + "': type must be single_turn or multi_turn");
        }
        if (type.equals("single_turn") && (r.input() == null || r.input().isBlank())) {
            throw bad("Scenario '" + id + "': 'input' is required for single_turn scenarios");
        }
        if (type.equals("multi_turn") && (r.persona() == null || r.goal() == null)) {
            throw bad("Scenario '" + id + "': multi_turn scenarios need 'persona' and 'goal'");
        }
        if (r.input() != null && r.input().length() > 8000) {
            throw bad("Scenario '" + id + "': input is too long (max 8000 characters)");
        }
        List<Map<String, Object>> evals = r.evaluators() == null ? new ArrayList<Map<String, Object>>() : r.evaluators();
        if (evals.size() > 20) {
            throw bad("Scenario '" + id + "': at most 20 evaluators");
        }
        for (Map<String, Object> cfg : evals) {
            Object t = cfg.get("type");
            if (!(t instanceof String)) {
                throw bad("Scenario '" + id + "': every evaluator needs a 'type'");
            }
            Evaluator ev = registry.find((String) t).orElseThrow(() ->
                    bad("Scenario '" + id + "': unknown evaluator '" + t + "'. Known: " + registry.types()));
            try {
                ev.validate(cfg);
            } catch (IllegalArgumentException e) {
                throw bad("Scenario '" + id + "', evaluator '" + t + "': " + e.getMessage());
            }
        }
    }

    private static String normalizedType(ScenarioRequest r) {
        return r.type() == null || r.type().isBlank() ? "single_turn" : r.type().trim();
    }

    private static void apply(Scenario s, ScenarioRequest r) {
        s.setInput(r.input());
        s.setPersona(r.persona());
        s.setGoal(r.goal());
        s.setMaxTurns(r.maxTurns());
        s.setTags(Json.write(r.tags() == null ? new ArrayList<String>() : r.tags()));
        s.setEvaluators(Json.write(r.evaluators() == null ? new ArrayList<Map<String, Object>>() : r.evaluators()));
    }

    private SuiteResponse toResponse(TestSuite s) {
        return new SuiteResponse(s.getId(), s.getName(), s.getDescription(),
                scenarios.countBySuiteId(s.getId()), s.getCreatedAt());
    }

    private static ScenarioResponse toResponse(Scenario s) {
        return new ScenarioResponse(s.getId(), s.getExternalId(), s.getType(), s.getInput(), s.getPersona(),
                s.getGoal(), s.getMaxTurns(), Json.list(s.getTags()), Json.list(s.getEvaluators()), s.getCreatedAt());
    }

    private static ApiException bad(String msg) {
        return new ApiException(HttpStatus.BAD_REQUEST, msg);
    }
}
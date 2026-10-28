package com.agentprobe.suite;

import com.agentprobe.api.ApiException;
import com.agentprobe.suite.SuiteDtos.ScenarioRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads and writes the "tests as code" YAML format. Uses SafeConstructor so YAML can't create arbitrary objects. */
@Component
public class ScenarioYaml {

    public record ParsedSuite(String name, String description, List<ScenarioRequest> scenarios) {
    }

    public ParsedSuite parse(String yaml) {
        Object root;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setCodePointLimit(200_000);
            options.setMaxAliasesForCollections(10);
            options.setAllowDuplicateKeys(false);
            root = new Yaml(new SafeConstructor(options)).load(yaml);
        } catch (YAMLException e) {
            throw bad("Invalid YAML: " + shorten(e.getMessage()));
        }
        if (!(root instanceof Map)) {
            throw bad("YAML must be a mapping with 'suite' and 'scenarios'");
        }
        Map<?, ?> top = (Map<?, ?>) root;

        String name = text(top.get("suite"));
        if (name == null || name.isBlank()) {
            throw bad("Missing 'suite' (the suite name)");
        }
        if (name.length() > 120) {
            throw bad("Suite name is too long (max 120 characters)");
        }
        String description = text(top.get("description"));

        if (!(top.get("scenarios") instanceof List)) {
            throw bad("Missing 'scenarios' list");
        }
        List<ScenarioRequest> out = new ArrayList<ScenarioRequest>();
        int index = 0;
        for (Object item : (List<?>) top.get("scenarios")) {
            index++;
            if (!(item instanceof Map)) {
                throw bad("Scenario #" + index + " must be a mapping");
            }
            Map<?, ?> s = (Map<?, ?>) item;
            Integer maxTurns = s.get("max_turns") instanceof Number ? ((Number) s.get("max_turns")).intValue() : null;
            out.add(new ScenarioRequest(
                    text(s.get("id")),
                    text(s.get("type")),
                    text(s.get("input")),
                    text(s.get("persona")),
                    text(s.get("goal")),
                    maxTurns,
                    strings(s.get("tags"), index),
                    evaluators(s.get("evaluators"), index)));
        }
        return new ParsedSuite(name.trim(), description, out);
    }

    public String dump(Map<String, Object> root) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setWidth(120);
        return new Yaml(options).dump(root);
    }

    // ---------------------------------------------------------------- helpers

    private static List<String> strings(Object o, int index) {
        List<String> out = new ArrayList<String>();
        if (o == null) {
            return out;
        }
        if (!(o instanceof List)) {
            throw bad("Scenario #" + index + ": 'tags' must be a list");
        }
        for (Object t : (List<?>) o) {
            out.add(String.valueOf(t));
        }
        return out;
    }

    private static List<Map<String, Object>> evaluators(Object o, int index) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        if (o == null) {
            return out;
        }
        if (!(o instanceof List)) {
            throw bad("Scenario #" + index + ": 'evaluators' must be a list");
        }
        for (Object e : (List<?>) o) {
            if (!(e instanceof Map)) {
                throw bad("Scenario #" + index + ": each evaluator must be a mapping with a 'type'");
            }
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) e).entrySet()) {
                m.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            out.add(m);
        }
        return out;
    }

    private static String text(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String shorten(String s) {
        if (s == null) {
            return "";
        }
        String oneLine = s.replace('\n', ' ');
        return oneLine.length() > 200 ? oneLine.substring(0, 200) + "..." : oneLine;
    }

    private static ApiException bad(String msg) {
        return new ApiException(HttpStatus.BAD_REQUEST, msg);
    }
}
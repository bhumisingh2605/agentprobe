package com.agentprobe.cli;

import net.minidev.json.JSONValue;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

/**
 * AgentProbe CI client.
 * Exit codes: 0 = pass (or warn when --fail-on FAIL), 1 = verdict triggers failure,
 * 2 = usage or server error / no baseline, 3 = run did not complete.
 */
@Command(name = "agentprobe", mixinStandardHelpOptions = true, version = "agentprobe-cli 0.1.0",
        description = "Run AgentProbe test suites from CI and gate pull requests on the result.",
        subcommands = {Main.RunCmd.class})
public class Main implements Runnable {

    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    private static final Pattern UUID_RE = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private record Resp(int status, String body) {
    }

    @Command(name = "run", mixinStandardHelpOptions = true,
            description = "Run a suite against an agent version, compare to the baseline, write a markdown report.")
    static class RunCmd implements Callable<Integer> {

        @Option(names = "--url", defaultValue = "${env:AGENTPROBE_URL:-http://localhost:8081}",
                description = "AgentProbe base URL (env AGENTPROBE_URL)")
        String url;

        @Option(names = "--token", defaultValue = "${env:AGENTPROBE_TOKEN:-}",
                description = "API token starting with apt_ (env AGENTPROBE_TOKEN)")
        String token;

        @Option(names = "--project", required = true, description = "Project id")
        String project;

        @Option(names = "--suite", required = true, description = "Suite id or name")
        String suite;

        @Option(names = "--agent", required = true, description = "Agent id")
        String agent;

        @Option(names = "--agent-version", required = true, description = "Agent version id or label")
        String agentVersion;

        @Option(names = "--trials", defaultValue = "3", description = "Trials per scenario (1-5)")
        int trials;

        @Option(names = "--concurrency", defaultValue = "2", description = "Scenarios in parallel (1-8)")
        int concurrency;

        @Option(names = "--token-budget", description = "Stop when this many tokens (estimate) are used")
        Integer tokenBudget;

        @Option(names = "--fail-on", defaultValue = "FAIL", description = "FAIL (default) or WARN")
        String failOn;

        @Option(names = "--report", defaultValue = "agentprobe-report.md", description = "Markdown report file")
        String report;

        @Option(names = "--timeout-minutes", defaultValue = "60", description = "Give up waiting after this long")
        int timeoutMinutes;

        @Option(names = "--set-baseline-if-missing",
                description = "If the suite has no baseline yet, mark this run as the baseline and exit 0")
        boolean setBaselineIfMissing;

        private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        @Override
        public Integer call() throws Exception {
            if (token == null || token.isBlank()) {
                System.err.println("Missing API token. Pass --token or set AGENTPROBE_TOKEN.");
                return 2;
            }
            String base = url.replaceAll("/+$", "") + "/api/projects/" + project;
            try {
                String suiteId = resolve(base + "/suites", suite, "name");
                String versionId = resolve(base + "/agents/" + agent + "/versions", agentVersion, "versionLabel");

                String body = "{\"agentId\":\"" + agent + "\",\"versionId\":\"" + versionId + "\",\"trials\":" + trials
                        + ",\"concurrency\":" + concurrency
                        + (tokenBudget != null ? ",\"tokenBudget\":" + tokenBudget : "") + "}";
                Resp started = send("POST", base + "/suites/" + suiteId + "/runs", body, "application/json");
                if (started.status() != 202) {
                    System.err.println("Could not start the run: HTTP " + started.status() + " " + started.body());
                    return 2;
                }
                String runId = String.valueOf(map(JSONValue.parse(started.body())).get("id"));
                System.out.println("Started run " + runId);

                String status = waitForRun(base, runId);
                if (!"COMPLETED".equals(status)) {
                    String msg = "AgentProbe run ended with status " + status + ". No comparison was made.";
                    System.err.println(msg);
                    Files.writeString(Path.of(report), "## AgentProbe: run did not complete\n\n" + msg + "\n");
                    return 3;
                }

                Resp cmp = send("GET", base + "/runs/" + runId + "/compare-to-baseline", null, null);
                if (cmp.status() == 404 && cmp.body().contains("No baseline")) {
                    if (setBaselineIfMissing) {
                        Resp b = send("POST", base + "/runs/" + runId + "/baseline", null, null);
                        String msg = b.status() < 300 ? "No baseline existed. This run is now the baseline."
                                : "Could not mark the baseline: HTTP " + b.status();
                        System.out.println(msg);
                        Files.writeString(Path.of(report), "## AgentProbe\n\n" + msg + "\n");
                        return b.status() < 300 ? 0 : 2;
                    }
                    System.err.println("No baseline for this suite and agent. Re-run with --set-baseline-if-missing"
                            + " on a known-good version.");
                    return 2;
                }
                if (cmp.status() != 200) {
                    System.err.println("Comparison failed: HTTP " + cmp.status() + " " + cmp.body());
                    return 2;
                }

                Resp md = send("GET", base + "/runs/" + runId + "/compare-to-baseline/report", null, null);
                String markdown = md.status() == 200 ? md.body() : "Report unavailable (HTTP " + md.status() + ")";
                Files.writeString(Path.of(report), markdown);
                System.out.println(markdown);

                String verdict = String.valueOf(map(map(JSONValue.parse(cmp.body())).get("comparison")).get("verdict"));
                System.out.println("Verdict: " + verdict);
                boolean fail = "FAIL".equals(verdict) || ("WARN".equalsIgnoreCase(failOn) && "WARN".equals(verdict));
                return fail ? 1 : 0;
            } catch (IllegalStateException | IOException e) {
                System.err.println("Error: " + e.getMessage());
                return 2;
            }
        }

        private String waitForRun(String base, String runId) throws IOException, InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMinutes * 60_000L;
            String last = "";
            while (true) {
                Resp r = send("GET", base + "/runs/" + runId, null, null);
                if (r.status() != 200) {
                    throw new IllegalStateException("Could not read run: HTTP " + r.status());
                }
                Map<String, Object> run = map(map(JSONValue.parse(r.body())).get("run"));
                String status = String.valueOf(run.get("status"));
                String line = status + " " + (num(run.get("passedCount")) + num(run.get("failedCount")))
                        + "/" + num(run.get("totalScenarios"));
                if (!line.equals(last)) {
                    System.out.println(line);
                    last = line;
                }
                if (!"QUEUED".equals(status) && !"RUNNING".equals(status)) {
                    return status;
                }
                if (System.currentTimeMillis() > deadline) {
                    send("POST", base + "/runs/" + runId + "/cancel", null, null);
                    return "TIMED_OUT";
                }
                Thread.sleep(5000);
            }
        }

        /** Accepts an id, or finds the item by a name field in the list endpoint. */
        private String resolve(String listUrl, String idOrName, String nameField) throws IOException, InterruptedException {
            if (UUID_RE.matcher(idOrName).matches()) {
                return idOrName;
            }
            Resp r = send("GET", listUrl, null, null);
            if (r.status() != 200) {
                throw new IllegalStateException("Could not list " + listUrl + ": HTTP " + r.status());
            }
            Object parsed = JSONValue.parse(r.body());
            if (parsed instanceof List<?> list) {
                for (Object o : list) {
                    Map<String, Object> m = map(o);
                    if (idOrName.equals(String.valueOf(m.get(nameField)))) {
                        return String.valueOf(m.get("id"));
                    }
                }
            }
            throw new IllegalStateException("No item named '" + idOrName + "' at " + listUrl);
        }

        private Resp send(String method, String uri, String body, String contentType) throws IOException, InterruptedException {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(uri))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + token);
            HttpRequest.BodyPublisher pub = body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
            if (contentType != null) {
                b.header("Content-Type", contentType);
            }
            HttpResponse<String> res = http.send(b.method(method, pub).build(), HttpResponse.BodyHandlers.ofString());
            return new Resp(res.statusCode(), res.body() == null ? "" : res.body());
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> map(Object o) {
            if (o instanceof Map) {
                return (Map<String, Object>) o;
            }
            throw new IllegalStateException("Unexpected response from the server");
        }

        private static int num(Object o) {
            return o instanceof Number n ? n.intValue() : 0;
        }
    }
}
package com.agentprobe.run;

import com.agentprobe.api.ApiException;
import com.agentprobe.calibration.Agreement;
import com.agentprobe.run.LabelingDtos.CalibrationResponse;
import com.agentprobe.run.LabelingDtos.LabelDto;
import com.agentprobe.run.LabelingDtos.LabelTask;
import com.agentprobe.run.LabelingDtos.NextResponse;
import com.agentprobe.run.LabelingDtos.SubmitRequest;
import com.agentprobe.run.LabelingDtos.TurnView;
import com.agentprobe.suite.SuiteService;
import com.agentprobe.util.Json;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class LabelingService {

    private static final String JUDGE = "llm_judge";

    private final SuiteService suiteService;
    private final TestRunRepository runs;
    private final ScenarioResultRepository results;
    private final EvaluationRecordRepository evaluations;
    private final TurnRecordRepository turnRepo;
    private final HumanLabelRepository labels;

    public LabelingService(SuiteService suiteService, TestRunRepository runs, ScenarioResultRepository results,
                           EvaluationRecordRepository evaluations, TurnRecordRepository turnRepo,
                           HumanLabelRepository labels) {
        this.suiteService = suiteService;
        this.runs = runs;
        this.results = results;
        this.evaluations = evaluations;
        this.turnRepo = turnRepo;
        this.labels = labels;
    }

    // ------------------------------------------------------------------ next task (blind)

    @Transactional(readOnly = true)
    public NextResponse next(UUID projectId, UUID runId, UUID userId, boolean judgedOnly) {
        suiteService.requireProject(projectId, userId);
        runs.findByIdAndProjectId(runId, projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Run not found"));

        List<ScenarioResult> all = results.findAllByRunIdOrderByPositionAsc(runId);
        if (all.isEmpty()) {
            return new NextResponse(null, 0, 0);
        }
        List<UUID> ids = new ArrayList<UUID>();
        for (ScenarioResult r : all) {
            ids.add(r.getId());
        }
        Set<UUID> judged = new HashSet<UUID>();
        for (EvaluationRecord e : evaluations.findAllByScenarioResultIdIn(ids)) {
            if (JUDGE.equals(e.getEvaluatorType())) {
                judged.add(e.getScenarioResultId());
            }
        }
        Set<UUID> labeled = new HashSet<UUID>();
        for (HumanLabel l : labels.findAllByUserIdAndScenarioResultIdIn(userId, ids)) {
            labeled.add(l.getScenarioResultId());
        }

        List<ScenarioResult> pool = new ArrayList<ScenarioResult>();
        int total = 0;
        int done = 0;
        for (ScenarioResult r : all) {
            if (judgedOnly && !judged.contains(r.getId())) {
                continue;
            }
            total++;
            if (labeled.contains(r.getId())) {
                done++;
            } else {
                pool.add(r);
            }
        }
        if (pool.isEmpty()) {
            return new NextResponse(null, total, done);
        }
        ScenarioResult pick = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        List<TurnView> turns = new ArrayList<TurnView>();
        for (TurnRecord t : turnRepo.findAllByScenarioResultIdInOrderByTurnNumberAsc(List.of(pick.getId()))) {
            turns.add(new TurnView(t.getTurnNumber(), t.getRole(), t.getContent(), Json.list(t.getToolCalls())));
        }
        LabelTask task = new LabelTask(pick.getId(), pick.getExternalId(), pick.getTrial(), turns,
                Json.list(pick.getToolCalls()));
        return new NextResponse(task, total, done);
    }

    // ------------------------------------------------------------------ submit / list / delete

    @Transactional
    public LabelDto submit(UUID projectId, UUID userId, SubmitRequest req) {
        suiteService.requireProject(projectId, userId);
        ScenarioResult r = results.findById(req.scenarioResultId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Result not found"));
        runs.findByIdAndProjectId(r.getRunId(), projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Result not found"));

        HumanLabel label = labels.findByScenarioResultIdAndUserId(r.getId(), userId).orElse(null);
        if (label == null) {
            label = new HumanLabel(r.getId(), userId, req.passed(), req.score(), req.note());
        } else {
            label.update(req.passed(), req.score(), req.note());
        }
        HumanLabel saved = labels.save(label);
        return new LabelDto(saved.getId(), r.getId(), r.getRunId(), r.getExternalId(), r.getTrial(),
                saved.isPassed(), saved.getScore(), saved.getNote(), saved.getCreatedAt(), false, null, null);
    }

    @Transactional(readOnly = true)
    public List<LabelDto> list(UUID projectId, UUID userId) {
        suiteService.requireProject(projectId, userId);
        List<LabelDto> out = new ArrayList<LabelDto>();
        for (Joined j : collect(projectId, userId)) {
            HumanLabel l = j.label();
            ScenarioResult r = j.result();
            out.add(new LabelDto(l.getId(), r.getId(), r.getRunId(), r.getExternalId(), r.getTrial(),
                    l.isPassed(), l.getScore(), l.getNote(), l.getCreatedAt(), j.hasJudge(),
                    j.hasJudge() ? Boolean.valueOf(j.judgePassed()) : null,
                    j.hasJudge() ? Integer.valueOf(j.judgeScore()) : null));
        }
        return out;
    }

    @Transactional
    public void delete(UUID projectId, UUID userId, UUID labelId) {
        suiteService.requireProject(projectId, userId);
        HumanLabel l = labels.findByIdAndUserId(labelId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Label not found"));
        labels.delete(l);
    }

    // ------------------------------------------------------------------ calibration

    @Transactional(readOnly = true)
    public CalibrationResponse calibration(UUID projectId, UUID userId) {
        suiteService.requireProject(projectId, userId);
        List<Joined> rows = collect(projectId, userId);
        List<Agreement.Pair> pairs = new ArrayList<Agreement.Pair>();
        Set<String> judges = new LinkedHashSet<String>();
        for (Joined j : rows) {
            if (!j.hasJudge()) {
                continue;
            }
            pairs.add(new Agreement.Pair(j.label().isPassed(), j.judgePassed(), j.label().getScore(), j.judgeScore()));
            judges.addAll(j.judges());
        }
        return new CalibrationResponse(Agreement.compute(pairs), new ArrayList<String>(judges),
                rows.size(), pairs.size());
    }

    @Transactional(readOnly = true)
    public String exportCsv(UUID projectId, UUID userId) {
        suiteService.requireProject(projectId, userId);
        StringBuilder sb = new StringBuilder();
        sb.append("run_id,scenario_id,trial,human_passed,human_score,judge_passed,judge_score,judge,note\n");
        for (Joined j : collect(projectId, userId)) {
            sb.append(csv(j.result().getRunId().toString())).append(',')
                    .append(csv(j.result().getExternalId())).append(',')
                    .append(j.result().getTrial()).append(',')
                    .append(j.label().isPassed()).append(',')
                    .append(j.label().getScore()).append(',')
                    .append(j.hasJudge() ? String.valueOf(j.judgePassed()) : "").append(',')
                    .append(j.hasJudge() ? String.valueOf(j.judgeScore()) : "").append(',')
                    .append(csv(String.join(" | ", j.judges()))).append(',')
                    .append(csv(j.label().getNote())).append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ internals

    private record Joined(HumanLabel label, ScenarioResult result, boolean hasJudge, boolean judgePassed,
                          int judgeScore, Set<String> judges) {
    }

    private List<Joined> collect(UUID projectId, UUID userId) {
        List<HumanLabel> mine = labels.findAllByUserId(userId);
        List<Joined> out = new ArrayList<Joined>();
        if (mine.isEmpty()) {
            return out;
        }
        List<UUID> resultIds = new ArrayList<UUID>();
        for (HumanLabel l : mine) {
            resultIds.add(l.getScenarioResultId());
        }
        Map<UUID, ScenarioResult> byId = new HashMap<UUID, ScenarioResult>();
        Set<UUID> runIds = new HashSet<UUID>();
        for (ScenarioResult r : results.findAllById(resultIds)) {
            byId.put(r.getId(), r);
            runIds.add(r.getRunId());
        }
        Set<UUID> okRuns = new HashSet<UUID>();
        for (TestRun run : runs.findAllById(runIds)) {
            if (run.getProjectId().equals(projectId)) {
                okRuns.add(run.getId());
            }
        }
        List<UUID> okResultIds = new ArrayList<UUID>();
        for (ScenarioResult r : byId.values()) {
            if (okRuns.contains(r.getRunId())) {
                okResultIds.add(r.getId());
            }
        }
        Map<UUID, List<EvaluationRecord>> judgeEvals = new HashMap<UUID, List<EvaluationRecord>>();
        if (!okResultIds.isEmpty()) {
            for (EvaluationRecord e : evaluations.findAllByScenarioResultIdIn(okResultIds)) {
                if (JUDGE.equals(e.getEvaluatorType())) {
                    judgeEvals.computeIfAbsent(e.getScenarioResultId(), k -> new ArrayList<EvaluationRecord>()).add(e);
                }
            }
        }
        for (HumanLabel l : mine) {
            ScenarioResult r = byId.get(l.getScenarioResultId());
            if (r == null || !okRuns.contains(r.getRunId())) {
                continue;
            }
            List<EvaluationRecord> je = judgeEvals.get(r.getId());
            if (je == null || je.isEmpty()) {
                out.add(new Joined(l, r, false, false, 0, new LinkedHashSet<String>()));
                continue;
            }
            boolean allPassed = true;
            double scoreSum = 0;
            Set<String> judges = new LinkedHashSet<String>();
            for (EvaluationRecord e : je) {
                allPassed = allPassed && e.isPassed();
                scoreSum += e.getScore();
                judges.add(e.getJudgeModel() + "/" + e.getJudgePromptVersion());
            }
            int judgeScore = (int) Math.round(scoreSum / je.size() * 5.0);
            judgeScore = Math.max(1, Math.min(5, judgeScore));
            out.add(new Joined(l, r, true, allPassed, judgeScore, judges));
        }
        return out;
    }

    /** Quotes the value and neutralises spreadsheet formulas (CSV injection). */
    static String csv(String v) {
        if (v == null) {
            return "";
        }
        String s = v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
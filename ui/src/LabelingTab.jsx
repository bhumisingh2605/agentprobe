import { useEffect, useState } from 'react';
import { api, download } from './api.js';
import { Err, Transcript, fmt, pct, useAsync } from './ui.jsx';

export default function LabelingTab({ project }) {
  const pid = project.id;
  const suites = useAsync(() => api(`/api/projects/${pid}/suites`), [pid]);
  const [suiteId, setSuiteId] = useState('');
  const runs = useAsync(
    () => (suiteId ? api(`/api/projects/${pid}/suites/${suiteId}/runs`) : Promise.resolve([])), [suiteId]);
  const [runId, setRunId] = useState('');
  const calibration = useAsync(() => api(`/api/projects/${pid}/labeling/calibration`), [pid]);

  return (
    <div>
      <div className="card">
        <h3>Blind labeling</h3>
        <p className="mute">
          You see only the conversation, never the judge's verdict. Decide whether the agent did the right thing,
          then give a 1-5 quality score. Label at least 50 judged results for a meaningful agreement number.
        </p>
        <div className="row">
          <select value={suiteId} onChange={(e) => { setSuiteId(e.target.value); setRunId(''); }}>
            <option value="">Suite...</option>
            {(suites.data || []).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
          <select value={runId} onChange={(e) => setRunId(e.target.value)}>
            <option value="">Run...</option>
            {(runs.data || []).filter((r) => r.status === 'COMPLETED').map((r) => (
              <option key={r.id} value={r.id}>{new Date(r.startedAt).toLocaleString()} · {r.totalScenarios} results</option>
            ))}
          </select>
        </div>
        {runId && <Labeler key={runId} pid={pid} runId={runId} onLabeled={calibration.reload} />}
      </div>
      <Calibration pid={pid} state={calibration} />
    </div>
  );
}

function Labeler({ pid, runId, onLabeled }) {
  const next = useAsync(() => api(`/api/projects/${pid}/labeling/next?runId=${runId}`), [pid, runId]);
  const [passed, setPassed] = useState(null);
  const [score, setScore] = useState(0);
  const [note, setNote] = useState('');
  const [error, setError] = useState(null);
  const task = next.data?.task;

  useEffect(() => { setPassed(null); setScore(0); setNote(''); }, [task?.scenarioResultId]);

  async function submit() {
    setError(null);
    try {
      await api(`/api/projects/${pid}/labeling/labels`, {
        method: 'POST',
        body: { scenarioResultId: task.scenarioResultId, passed, score, note: note || null },
      });
      onLabeled();
      next.reload();
    } catch (x) { setError(x); }
  }

  if (next.error) return <Err error={next.error} />;
  if (!next.data) return <div className="mute">Loading...</div>;
  if (!task) {
    return <p className="ok">All {next.data.total} judged results of this run are labeled. Pick another run, or run a suite with llm_judge evaluators.</p>;
  }
  return (
    <div style={{ marginTop: 12 }}>
      <div className="mute">Labeled {next.data.labeled} of {next.data.total} · scenario <span className="mono">{task.scenarioId}</span></div>
      <Transcript turns={task.turns} />
      <div className="row" style={{ marginTop: 10 }}>
        <b>Did the agent do the right thing?</b>
        <button className={passed === true ? 'pri' : ''} onClick={() => setPassed(true)}>Pass</button>
        <button className={passed === false ? 'pri' : ''} onClick={() => setPassed(false)}>Fail</button>
        <b>Quality</b>
        {[1, 2, 3, 4, 5].map((n) => (
          <button key={n} className={score === n ? 'pri' : ''} onClick={() => setScore(n)}>{n}</button>
        ))}
      </div>
      <div className="row" style={{ marginTop: 8 }}>
        <input style={{ flex: 1 }} placeholder="Optional note" value={note} onChange={(e) => setNote(e.target.value)} />
        <button className="pri" disabled={passed === null || score === 0} onClick={submit}>Save and next</button>
      </div>
      <Err error={error} />
    </div>
  );
}

function Calibration({ pid, state }) {
  const r = state.data?.report;
  return (
    <div className="card">
      <div className="row">
        <h3 style={{ margin: 0 }}>Judge calibration</h3>
        <button onClick={state.reload}>Refresh</button>
        <button onClick={() => download(`/api/projects/${pid}/labeling/export.csv`, 'labels.csv')}>Export CSV</button>
      </div>
      <Err error={state.error} />
      {r && (
        <>
          <p className="mute">
            {state.data.labeledWithJudge} labeled results with a judge verdict ({state.data.labeledTotal} labels in total).
            Judge: {state.data.judges.join(', ') || '-'}
          </p>
          {r.n > 0 && (
            <div className="grid2">
              <table>
                <tbody>
                  <tr><td>Percent agreement (pass/fail)</td><td><b>{pct(r.percentAgreement)}</b></td></tr>
                  <tr><td>Cohen's kappa</td><td><b>{fmt(r.cohensKappa)}</b> <span className="mute">({r.kappaInterpretation})</span></td></tr>
                  <tr><td>Exact score agreement</td><td>{pct(r.exactScoreAgreement)}</td></tr>
                  <tr><td>Score within ±1</td><td>{pct(r.within1ScoreAgreement)}</td></tr>
                  <tr><td>Mean absolute score error</td><td>{fmt(r.meanAbsScoreError)}</td></tr>
                  <tr><td>Judge pass rate minus human</td><td>{fmt(r.judgePassRateMinusHuman)}</td></tr>
                </tbody>
              </table>
              <table>
                <thead><tr><th /><th>Judge pass</th><th>Judge fail</th></tr></thead>
                <tbody>
                  <tr><th>Human pass</th><td>{r.bothPass}</td><td>{r.humanPassJudgeFail}</td></tr>
                  <tr><th>Human fail</th><td>{r.humanFailJudgePass}</td><td>{r.bothFail}</td></tr>
                </tbody>
              </table>
            </div>
          )}
          {r.warnings.map((w, i) => <div key={i} className="err">{w}</div>)}
        </>
      )}
    </div>
  );
}
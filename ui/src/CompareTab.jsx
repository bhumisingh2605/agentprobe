import { useState } from 'react';
import { api } from './api.js';
import { Badge, Err, Transcript, fmt, pct, useAsync } from './ui.jsx';

export default function CompareTab({ project }) {
  const pid = project.id;
  const suites = useAsync(() => api(`/api/projects/${pid}/suites`), [pid]);
  const [suiteId, setSuiteId] = useState('');
  const runs = useAsync(
    () => (suiteId ? api(`/api/projects/${pid}/suites/${suiteId}/runs`) : Promise.resolve([])), [suiteId]);
  const [base, setBase] = useState('');
  const [cand, setCand] = useState('');
  const [cmp, setCmp] = useState(null);
  const [error, setError] = useState(null);
  const [scenario, setScenario] = useState(null);

  const completed = (runs.data || []).filter((r) => r.status === 'COMPLETED');
  const label = (r) => `${new Date(r.startedAt).toLocaleString()} · ${pct(r.passRate)} · ${r.trials}x`;

  async function compare(path) {
    setError(null);
    setCmp(null);
    setScenario(null);
    try { setCmp(await api(path)); } catch (x) { setError(x); }
  }

  return (
    <div>
      <div className="card row">
        <select value={suiteId} onChange={(e) => { setSuiteId(e.target.value); setBase(''); setCand(''); setCmp(null); }}>
          <option value="">Suite...</option>
          {(suites.data || []).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
        <select value={base} onChange={(e) => setBase(e.target.value)}>
          <option value="">Baseline run...</option>
          {completed.map((r) => <option key={r.id} value={r.id}>{label(r)}</option>)}
        </select>
        <select value={cand} onChange={(e) => setCand(e.target.value)}>
          <option value="">Candidate run...</option>
          {completed.map((r) => <option key={r.id} value={r.id}>{label(r)}</option>)}
        </select>
        <button className="pri" disabled={!base || !cand}
                onClick={() => compare(`/api/projects/${pid}/runs/compare?baseRunId=${base}&candidateRunId=${cand}`)}>
          Compare
        </button>
        <button disabled={!cand}
                onClick={() => compare(`/api/projects/${pid}/runs/${cand}/compare-to-baseline`)}>
          Candidate vs saved baseline
        </button>
        <Err error={error} />
      </div>
      {cmp && <Result pid={pid} cmp={cmp} scenario={scenario} setScenario={setScenario} />}
    </div>
  );
}

function Result({ pid, cmp, scenario, setScenario }) {
  const c = cmp.comparison;
  const rows = [
    ['Pass rate', pct(c.baseline.passRate), pct(c.candidate.passRate)],
    ['Mean score', fmt(c.baseline.meanScore), fmt(c.candidate.meanScore)],
    ['Avg latency', `${c.baseline.avgLatencyMs} ms`, `${c.candidate.avgLatencyMs} ms`],
    ['p95 latency', `${c.baseline.p95LatencyMs} ms`, `${c.candidate.p95LatencyMs} ms`],
    ['Agent tokens', c.baseline.totalTokens, c.candidate.totalTokens],
    ['Executions', c.baseline.results, c.candidate.results],
  ];
  return (
    <>
      <div className="card">
        <div className="row">
          <h3 style={{ margin: 0 }}>Verdict</h3><Badge v={c.verdict} />
          <span className="mute">{cmp.baseVersion} → {cmp.candidateVersion}</span>
        </div>
        <ul>{c.reasons.map((r, i) => <li key={i}>{r}</li>)}</ul>
        <table>
          <thead><tr><th>Metric</th><th>Baseline</th><th>Candidate</th></tr></thead>
          <tbody>{rows.map((r) => <tr key={r[0]}><td>{r[0]}</td><td>{r[1]}</td><td>{r[2]}</td></tr>)}</tbody>
        </table>
      </div>
      <div className="card">
        <h3>Scenarios</h3>
        <table>
          <thead><tr><th>Scenario</th><th>Status</th><th>Baseline</th><th>Candidate</th><th>Flaky</th></tr></thead>
          <tbody>
            {c.scenarios.map((s) => (
              <tr key={s.scenarioId} className="click" onClick={() => setScenario(s.scenarioId)}>
                <td className="mono">{s.scenarioId}</td>
                <td><Badge v={s.status} /></td>
                <td>{pct(s.basePassRate)}</td><td>{pct(s.candidatePassRate)}</td>
                <td>{s.flaky ? 'yes' : ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {scenario && <SideBySide pid={pid} cmp={cmp} scenarioId={scenario} />}
    </>
  );
}

function SideBySide({ pid, cmp, scenarioId }) {
  const a = useAsync(() => api(`/api/projects/${pid}/runs/${cmp.baseRunId}`), [cmp.baseRunId]);
  const b = useAsync(() => api(`/api/projects/${pid}/runs/${cmp.candidateRunId}`), [cmp.candidateRunId]);
  const [trial, setTrial] = useState(1);
  const pick = (d) => (d.data?.results || []).filter((r) => r.scenarioId === scenarioId);
  const left = pick(a).find((r) => r.trial === trial) || pick(a)[0];
  const right = pick(b).find((r) => r.trial === trial) || pick(b)[0];
  const maxTrial = Math.max(1, ...pick(a).map((r) => r.trial), ...pick(b).map((r) => r.trial));

  return (
    <div className="card">
      <div className="row">
        <h3 style={{ margin: 0 }}>{scenarioId}</h3>
        <label>Trial <select value={trial} onChange={(e) => setTrial(Number(e.target.value))}>
          {Array.from({ length: maxTrial }, (_, i) => i + 1).map((t) => <option key={t}>{t}</option>)}
        </select></label>
      </div>
      <Err error={a.error || b.error} />
      <div className="grid2">
        {[['Baseline ' + cmp.baseVersion, left], ['Candidate ' + cmp.candidateVersion, right]].map(([title, r]) => (
          <div key={title}>
            <h4>{title} {r && <Badge v={r.passed ? 'PASS' : 'FAIL'} />}</h4>
            {r ? (
              <>
                <Transcript turns={r.turns} />
                {r.evaluations.filter((e) => !e.passed).map((e, i) => (
                  <div key={i} className="mono err">✗ {e.type}: {e.reasoning}</div>
                ))}
              </>
            ) : <div className="mute">Not in this run.</div>}
          </div>
        ))}
      </div>
    </div>
  );
}
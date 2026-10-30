
import { useEffect, useState } from 'react';
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { api } from './api.js';
import { Badge, Err, Transcript, fmt, pct, useAsync, usePoll } from './ui.jsx';

export default function RunsPanel({ pid, suite }) {
  const runs = useAsync(
    () => api(`/api/projects/${pid}/suites/${suite.id}/runs`),
    [pid, suite.id]
  );

  const [sel, setSel] = useState(null);

  const anyActive = (runs.data || []).some(
    (r) => r.status === 'QUEUED' || r.status === 'RUNNING'
  );

  usePoll(runs.reload, 3000, anyActive);

  useEffect(() => {
    const h = () => runs.reload();

    window.addEventListener('agentprobe-runs-changed', h);

    return () => {
      window.removeEventListener('agentprobe-runs-changed', h);
    };
  }, [runs.reload]);

  const chart = [...(runs.data || [])].reverse().map((r) => ({
    name: r.startedAt
      ? new Date(r.startedAt).toLocaleString([], {
          month: 'short',
          day: 'numeric',
          hour: '2-digit',
          minute: '2-digit',
        })
      : 'Pending',
    passRate: Math.round((r.passRate || 0) * 100),
    latency: r.avgLatencyMs || 0,
  }));

  return (
    <div className="card">
      <h3>Run history</h3>

      <Err error={runs.error} />

      {chart.length > 1 && (
        <div style={{ height: 220 }}>
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={chart}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="name" fontSize={11} />
              <YAxis yAxisId="l" domain={[0, 100]} unit="%" fontSize={11} />
              <YAxis
                yAxisId="r"
                orientation="right"
                unit=" ms"
                fontSize={11}
              />
              <Tooltip />
              <Legend />
              <Line
                yAxisId="l"
                type="monotone"
                dataKey="passRate"
                name="Pass rate"
                stroke="#2a5bd7"
              />
              <Line
                yAxisId="r"
                type="monotone"
                dataKey="latency"
                name="Avg latency"
                stroke="#b7791f"
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      )}

      <table>
        <thead>
          <tr>
            <th>Started</th>
            <th>Status</th>
            <th>Pass rate</th>
            <th>Done</th>
            <th>Trials</th>
            <th>Avg latency</th>
          </tr>
        </thead>

        <tbody>
          {(runs.data || []).map((r) => (
            <tr
              key={r.id}
              className="click"
              onClick={() => setSel(r.id)}
            >
              <td>
                {r.startedAt
                  ? new Date(r.startedAt).toLocaleString()
                  : '—'}
              </td>
              <td><Badge v={r.status} /></td>
              <td>{pct(r.passRate)}</td>
              <td>
                {(r.passedCount || 0) + (r.failedCount || 0)}/
                {r.totalScenarios ?? 0}
              </td>
              <td>{r.trials ?? 0}</td>
              <td>{r.avgLatencyMs ?? 0} ms</td>
            </tr>
          ))}
        </tbody>
      </table>

      {sel && (
        <RunDetail
          key={sel}
          pid={pid}
          runId={sel}
          onChange={runs.reload}
        />
      )}
    </div>
  );
}

export function RunDetail({ pid, runId, onChange }) {
  const d = useAsync(
    () => api(`/api/projects/${pid}/runs/${runId}`),
    [pid, runId]
  );

  const [open, setOpen] = useState(null);
  const [msg, setMsg] = useState(null);
  const [error, setError] = useState(null);

  const run = d.data?.run;
  const active =
    run && (run.status === 'QUEUED' || run.status === 'RUNNING');

  usePoll(d.reload, 2500, Boolean(active));

  async function act(path) {
    setError(null);
    setMsg(null);

    try {
      await api(`/api/projects/${pid}/runs/${runId}/${path}`, {
        method: 'POST',
      });

      setMsg(
        path === 'baseline' ? 'Marked as baseline' : 'Cancel requested'
      );

      d.reload();

      if (onChange) {
        onChange();
      }
    } catch (x) {
      setError(x);
    }
  }

  if (d.error) {
    return <Err error={d.error} />;
  }

  if (!d.data || !run) {
    return <div className="mute">Loading run details...</div>;
  }

  const done = (run.passedCount || 0) + (run.failedCount || 0);
  const total = run.totalScenarios || 0;
  const results = d.data.results || [];

  return (
    <div
      style={{
        marginTop: 16,
        borderTop: '1px solid var(--line)',
        paddingTop: 12,
      }}
    >
      <div className="row">
        <h4 style={{ margin: 0 }}>Run {String(run.id).slice(0, 8)}</h4>

        <Badge v={run.status} />

        <div className="bar">
          <i
            style={{
              width: `${total ? (done / total) * 100 : 0}%`,
            }}
          />
        </div>

        <span>{done}/{total}</span>

        {active && (
          <button className="danger" onClick={() => act('cancel')}>
            Cancel
          </button>
        )}

        {run.status === 'COMPLETED' && (
          <button onClick={() => act('baseline')}>
            Mark as baseline
          </button>
        )}

        {msg && <span className="ok">{msg}</span>}
      </div>

      {run.error && <div className="err">{run.error}</div>}

      <Err error={error} />

      <div className="mute" style={{ margin: '6px 0' }}>
        Judge tokens (estimate): {d.data.judgeTokens ?? 0} · Agent tokens:{' '}
        {run.totalTokens ?? 0}
      </div>

      <table>
        <thead>
          <tr>
            <th>Scenario</th>
            <th>Trial</th>
            <th>Result</th>
            <th>Score</th>
            <th>Latency</th>
            <th>Stop</th>
            <th>Error</th>
          </tr>
        </thead>

        <tbody>
          {results.map((r) => (
            <ResultRows
              key={r.id}
              r={r}
              open={open === r.id}
              toggle={() => setOpen(open === r.id ? null : r.id)}
            />
          ))}
        </tbody>
      </table>

      {results.length === 0 && (
        <div className="mute">No scenario results available yet.</div>
      )}
    </div>
  );
}

function ResultRows({ r, open, toggle }) {
  return (
    <>
      <tr className="click" onClick={toggle}>
        <td className="mono">{r.scenarioId}</td>
        <td>{r.trial}</td>
        <td><Badge v={r.passed ? 'PASS' : 'FAIL'} /></td>
        <td>{fmt(r.score)}</td>
        <td>{r.latencyMs ?? 0} ms</td>
        <td>{r.stopReason || '—'}</td>
        <td className="err">{r.error || '—'}</td>
      </tr>

      {open && (
        <tr>
          <td colSpan="7">
            <div className="grid2">
              <div>
                <h4>Transcript</h4>
                <Transcript turns={r.turns || []} />
              </div>

              <div>
                <h4>Evaluators</h4>

                {(r.evaluations || []).map((e, i) => (
                  <div key={i} style={{ marginBottom: 8 }}>
                    <Badge v={e.passed ? 'PASS' : 'FAIL'} />{' '}
                    <b>{e.type}</b>{' '}

                    <span className="mute">
                      {fmt(e.score)}
                      {e.judgeModel
                        ? ` · ${e.judgeModel} ${e.judgePromptVersion || ''}`
                        : ''}
                    </span>

                    <div className="mono">{e.reasoning || ''}</div>
                  </div>
                ))}

                {(r.evaluations || []).length === 0 && (
                  <div className="mute">No evaluator results available.</div>
                )}
              </div>
            </div>
          </td>
        </tr>
      )}
    </>
  );
}

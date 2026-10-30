import { useRef, useState } from 'react';
import { api, download } from './api.js';
import { Err, useAsync } from './ui.jsx';
import RunsPanel from './RunsPanel.jsx';

export default function SuitesTab({ project }) {
  const pid = project.id;
  const suites = useAsync(() => api(`/api/projects/${pid}/suites`), [pid]);
  const [sel, setSel] = useState(null);
  const [error, setError] = useState(null);
  const [msg, setMsg] = useState(null);
  const fileRef = useRef(null);

  async function onFile(e) {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setError(null);
    setMsg(null);
    try {
      const text = await file.text();
      // Backend wants raw YAML (text/plain), not JSON.
      const r = await api(`/api/projects/${pid}/suites/import`, { method: 'POST', text });
      setMsg(`Imported "${r.suiteName}": ${r.created} created, ${r.updated} updated`);
      await suites.reload();
      setSel(r.suiteId);
    } catch (x) {
      setError(x);
    }
  }

  async function remove(s) {
    if (!window.confirm(`Delete suite "${s.name}"?`)) return;
    setError(null);
    try {
      await api(`/api/projects/${pid}/suites/${s.id}`, { method: 'DELETE' });
      if (sel === s.id) setSel(null);
      suites.reload();
    } catch (x) {
      setError(x);
    }
  }

  const selected = (suites.data || []).find((s) => s.id === sel);

  return (
    <div>
      <div className="card row">
        <input ref={fileRef} type="file" accept=".yaml,.yml" hidden onChange={onFile} />
        <button className="pri" onClick={() => fileRef.current?.click()}>Import YAML</button>
        {msg && <span className="ok">{msg}</span>}
        <Err error={error || suites.error} />
      </div>

      <div className="card">
        <h3>Suites</h3>
        {suites.loading && !suites.data && <div className="mute">Loading…</div>}
        {suites.data && suites.data.length === 0 && (
          <div className="mute">No suites yet. Import a YAML file to get started.</div>
        )}
        {suites.data && suites.data.length > 0 && (
          <table>
            <thead><tr><th>Name</th><th>Scenarios</th><th /></tr></thead>
            <tbody>
              {suites.data.map((s) => (
                <tr key={s.id} className="click" onClick={() => setSel(s.id)}>
                  <td><b>{s.name}</b><div className="mute">{s.description}</div></td>
                  <td>{s.scenarioCount}</td>
                  <td className="row">
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        download(`/api/projects/${pid}/suites/${s.id}/export`, `${s.name}.yaml`).catch(setError);
                      }}
                    >
                      Export
                    </button>
                    <button
                      className="danger"
                      onClick={(e) => { e.stopPropagation(); remove(s); }}
                    >
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {selected && (
        <>
          <StartRun key={'s' + selected.id} pid={pid} suite={selected} />
          <RunsPanel key={selected.id} pid={pid} suite={selected} />
        </>
      )}
    </div>
  );
}

function StartRun({ pid, suite }) {
  const agents = useAsync(() => api(`/api/projects/${pid}/agents`), [pid]);
  const [agentId, setAgentId] = useState('');
  const [versionId, setVersionId] = useState('');
  const [trials, setTrials] = useState(1);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const versions = useAsync(
    () => (agentId ? api(`/api/projects/${pid}/agents/${agentId}/versions`) : Promise.resolve([])),
    [pid, agentId]
  );

  async function start() {
    setError(null);
    setBusy(true);
    try {
      await api(`/api/projects/${pid}/suites/${suite.id}/runs`, {
        method: 'POST',
        body: { agentId, versionId, trials: Number(trials), concurrency: 3 },
      });
      window.dispatchEvent(new Event('agentprobe-runs-changed'));
    } catch (x) {
      setError(x);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="card row">
      <b>Run "{suite.name}"</b>
      <select value={agentId} onChange={(e) => { setAgentId(e.target.value); setVersionId(''); }}>
        <option value="">Select agent…</option>
        {(agents.data || []).map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
      </select>
      <select value={versionId} onChange={(e) => setVersionId(e.target.value)} disabled={!agentId}>
        <option value="">Select version…</option>
        {(versions.data || []).map((v) => <option key={v.id} value={v.id}>{v.versionLabel}</option>)}
      </select>
      <label>
        Trials{' '}
        <input
          type="number"
          min="1"
          max="5"
          value={trials}
          onChange={(e) => setTrials(e.target.value)}
          style={{ width: 60 }}
        />
      </label>
      <button className="pri" disabled={!agentId || !versionId || busy} onClick={start}>
        {busy ? 'Starting…' : 'Start run'}
      </button>
      <Err error={error || agents.error || versions.error} />
    </div>
  );
}
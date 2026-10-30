
import { useState } from 'react';
import { api } from './api.js';
import { Err, useAsync } from './ui.jsx';

export default function AgentsTab({ project }) {
  const pid = project.id;

  const agents = useAsync(
    () => api(`/api/projects/${pid}/agents`),
    [pid]
  );

  const [name, setName] = useState('');
  const [error, setError] = useState(null);

  async function create(e) {
    e.preventDefault();
    setError(null);

    try {
      await api(`/api/projects/${pid}/agents`, {
        method: 'POST',
        body: { name, description: '' },
      });

      setName('');
      agents.reload();
    } catch (x) {
      setError(x);
    }
  }

  return (
    <div>
      <form className="card row" onSubmit={create}>
        <input
          placeholder="New agent name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
        />

        <button className="pri">Add agent</button>
        <Err error={error || agents.error} />
      </form>

      {(agents.data || []).map((a) => (
        <AgentCard key={a.id} pid={pid} agent={a} />
      ))}
    </div>
  );
}

function AgentCard({ pid, agent }) {
  const base = `/api/projects/${pid}/agents/${agent.id}`;

  const versions = useAsync(
    () => api(`${base}/versions`),
    [base]
  );

  const [f, setF] = useState({
    versionLabel: '',
    endpointUrl: 'http://localhost:8082/chat',
    authHeaderName: '',
    authHeaderValue: '',
    model: '',
  });

  const [error, setError] = useState(null);

  function set(field) {
    return (e) => {
      setF((previous) => ({
        ...previous,
        [field]: e.target.value,
      }));
    };
  }

  async function add(e) {
    e.preventDefault();
    setError(null);

    const body = { ...f };

    Object.keys(body).forEach((key) => {
      if (body[key] === '') {
        delete body[key];
      }
    });

    try {
      await api(`${base}/versions`, {
        method: 'POST',
        body,
      });

      setF((previous) => ({
        ...previous,
        versionLabel: '',
        authHeaderValue: '',
      }));

      versions.reload();
    } catch (x) {
      setError(x);
    }
  }

  return (
    <div className="card">
      <h3>{agent.name}</h3>

      <Err error={versions.error} />

      <table>
        <thead>
          <tr>
            <th>Version</th>
            <th>Endpoint</th>
            <th>Model</th>
            <th>Secret</th>
            <th />
          </tr>
        </thead>

        <tbody>
          {(versions.data || []).map((v) => (
            <VersionRow key={v.id} base={base} v={v} />
          ))}
        </tbody>
      </table>

      <h4>Register a version</h4>

      <form className="row" onSubmit={add}>
        <input
          placeholder="Label (prompt-v1)"
          value={f.versionLabel}
          onChange={set('versionLabel')}
          required
        />

        <input
          placeholder="Endpoint URL"
          value={f.endpointUrl}
          onChange={set('endpointUrl')}
          required
          style={{ minWidth: 260 }}
        />

        <input
          placeholder="Auth header name"
          value={f.authHeaderName}
          onChange={set('authHeaderName')}
        />

        <input
          placeholder="Auth header value"
          type="password"
          value={f.authHeaderValue}
          onChange={set('authHeaderValue')}
        />

        <input
          placeholder="Model"
          value={f.model}
          onChange={set('model')}
        />

        <button className="pri">Add</button>
      </form>

      <Err error={error} />
    </div>
  );
}

function VersionRow({ base, v }) {
  const [open, setOpen] = useState(false);

  return (
    <>
      <tr>
        <td><b>{v.versionLabel}</b></td>
        <td className="mono">{v.endpointUrl}</td>
        <td>{v.model || '-'}</td>
        <td>{v.hasAuthSecret ? 'stored (encrypted)' : '-'}</td>
        <td>
          <button onClick={() => setOpen((previous) => !previous)}>
            {open ? 'Close' : 'Try it'}
          </button>
        </td>
      </tr>

      {open && (
        <tr>
          <td colSpan="5">
            <Playground base={base} version={v} />
          </td>
        </tr>
      )}
    </>
  );
}

function Playground({ base, version }) {
  const url = `${base}/versions/${version.id}`;

  const [message, setMessage] = useState(
    'What is the status of order 1043?'
  );

  const [session, setSession] = useState(
    () => `ui-${Math.random().toString(36).slice(2, 8)}`
  );

  const [res, setRes] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function send(e) {
    e.preventDefault();

    setBusy(true);
    setError(null);
    setRes(null);

    try {
      const result = await api(`${url}/test`, {
        method: 'POST',
        body: {
          message,
          sessionId: session,
        },
      });

      setRes(result);
    } catch (x) {
      setError(x);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <form className="row" onSubmit={send}>
        <input
          style={{ flex: 1, minWidth: 260 }}
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          required
        />

        <input
          style={{ width: 120 }}
          value={session}
          onChange={(e) => setSession(e.target.value)}
          required
        />

        <button className="pri" disabled={busy}>
          {busy ? 'Sending...' : 'Send'}
        </button>
      </form>

      <Err error={error} />

      {res && (
        <div style={{ marginTop: 8 }}>
          <div className="bubble customer">
            {res.userMessage}
          </div>

          <div className="bubble agent">
            {res.agentAnswer ?? '(no answer found, check the response mapping)'}
          </div>

          <div className="mono mute">
            HTTP {res.httpStatus} · {res.latencyMs} ms · tools:{' '}
            {JSON.stringify(res.toolCalls)}
          </div>
        </div>
      )}
    </div>
  );
}

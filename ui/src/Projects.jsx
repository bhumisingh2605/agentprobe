
import { useState } from 'react';
import { api } from './api.js';
import { Err, useAsync } from './ui.jsx';

export default function Projects({ onSelect }) {
  const list = useAsync(() => api('/api/projects'), []);
  const [name, setName] = useState('');
  const [error, setError] = useState(null);

  async function create(e) {
    e.preventDefault();
    setError(null);

    try {
      const project = await api('/api/projects', {
        method: 'POST',
        body: { name, description: '' },
      });

      setName('');
      onSelect(project);
      list.reload();
    } catch (x) {
      setError(x);
    }
  }

  return (
    <div className="card">
      <h3>Projects</h3>

      <Err error={list.error} />

      <table>
        <tbody>
          {(list.data || []).map((p) => (
            <tr
              key={p.id}
              className="click"
              onClick={() => onSelect(p)}
            >
              <td><b>{p.name}</b></td>
              <td className="mute">{p.description}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <form
        className="row"
        onSubmit={create}
        style={{ marginTop: 12 }}
      >
        <input
          placeholder="New project name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
        />
        <button className="pri">Create</button>
      </form>

      <Err error={error} />
    </div>
  );
}

import { useState } from 'react';
import { signIn } from './api.js';
import { Err } from './ui.jsx';

export default function Login({ onDone }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [register, setRegister] = useState(false);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try { await signIn(email, password, register); onDone(); }
    catch (x) { setError(x); }
    finally { setBusy(false); }
  }

  return (
    <form className="card login" onSubmit={submit}>
      <h3>AgentProbe</h3>
      <input type="email" placeholder="Email" value={email} onChange={(e) => setEmail(e.target.value)} required />
      <input type="password" placeholder="Password (min 8 characters)" value={password}
             onChange={(e) => setPassword(e.target.value)} required />
      <Err error={error} />
      <div className="row">
        <button className="pri" disabled={busy}>{register ? 'Create account' : 'Sign in'}</button>
        <button type="button" onClick={() => setRegister(!register)}>
          {register ? 'I have an account' : 'Register'}
        </button>
      </div>
    </form>
  );
}
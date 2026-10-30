import { useEffect, useState } from 'react';
import { auth } from './api.js';
import Login from './Login.jsx';
import Projects from './Projects.jsx';
import AgentsTab from './AgentsTab.jsx';
import SuitesTab from './SuitesTab.jsx';
import CompareTab from './CompareTab.jsx';
import LabelingTab from './LabelingTab.jsx';

const TABS = [
  ['agents', 'Agents'],
  ['suites', 'Suites & runs'],
  ['compare', 'Compare'],
  ['labeling', 'Labeling'],
];

export default function App() {
  const [authed, setAuthed] = useState(!!auth.get());
  const [project, setProject] = useState(null);
  const [tab, setTab] = useState('agents');

  useEffect(() => {
    const onLogout = () => { setAuthed(false); setProject(null); };
    window.addEventListener('agentprobe-logout', onLogout);
    return () => window.removeEventListener('agentprobe-logout', onLogout);
  }, []);

  if (!authed) return <Login onDone={() => setAuthed(true)} />;

  return (
    <div>
      <header>
        <b>AgentProbe</b>
        {project && <span className="mute">/ {project.name}</span>}
        <span className="sp" />
        {project && <button onClick={() => setProject(null)}>Switch project</button>}
        <button onClick={() => { auth.clear(); setAuthed(false); setProject(null); }}>Sign out</button>
      </header>
      {!project ? (
        <main><Projects onSelect={setProject} /></main>
      ) : (
        <>
          <nav className="tabs">
            {TABS.map(([k, label]) => (
              <button key={k} className={tab === k ? 'on' : ''} onClick={() => setTab(k)}>{label}</button>
            ))}
          </nav>
          <main>
            {tab === 'agents' && <AgentsTab project={project} />}
            {tab === 'suites' && <SuitesTab project={project} />}
            {tab === 'compare' && <CompareTab project={project} />}
            {tab === 'labeling' && <LabelingTab project={project} />}
          </main>
        </>
      )}
    </div>
  );
}
import { useCallback, useEffect, useRef, useState } from 'react';

export function useAsync(fn, deps) {
  const [s, setS] = useState({ data: null, error: null, loading: true });
  const run = useCallback(() => {
    setS((p) => ({ ...p, loading: true, error: null }));
    return fn().then(
      (data) => setS({ data, error: null, loading: false }),
      (error) => setS({ data: null, error, loading: false }),
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  useEffect(() => { run(); }, [run]);
  return { ...s, reload: run };
}

export function usePoll(fn, ms, active) {
  const ref = useRef(fn);
  ref.current = fn;
  useEffect(() => {
    if (!active) return undefined;
    const t = setInterval(() => ref.current(), ms);
    return () => clearInterval(t);
  }, [ms, active]);
}

export const Err = ({ error }) => (error ? <div className="err">{error.message || String(error)}</div> : null);
export const pct = (x) => (x == null ? '-' : (x * 100).toFixed(0) + '%');
export const fmt = (x, d = 2) => (x == null ? '-' : Number(x).toFixed(d));

const BADGE = {
  PASS: 'ok', COMPLETED: 'ok', IMPROVED: 'ok', WARN: 'warn', DEGRADED: 'warn', RUNNING: 'warn',
  FAIL: 'bad', FAILED: 'bad', REGRESSED: 'bad', BUDGET_EXCEEDED: 'bad',
};
export const Badge = ({ v }) => <span className={'badge ' + (BADGE[v] || '')}>{String(v)}</span>;

export function Transcript({ turns }) {
  if (!turns || turns.length === 0) return <div className="mute">No turns stored.</div>;
  return (
    <div>
      {turns.map((t) => (
        <div key={t.number} className={'bubble ' + t.role}>
          <div className="mute mono">{t.role}{t.latencyMs ? ` · ${t.latencyMs} ms` : ''}</div>
          {t.content}
          {Array.isArray(t.toolCalls) && t.toolCalls.length > 0 && (
            <div className="mono mute">tools: {JSON.stringify(t.toolCalls)}</div>
          )}
        </div>
      ))}
    </div>
  );
}
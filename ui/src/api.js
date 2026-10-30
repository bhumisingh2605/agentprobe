const KEY = 'agentprobe.auth';

export const auth = {
  get() { try { return JSON.parse(localStorage.getItem(KEY)); } catch { return null; } },
  set(v) { localStorage.setItem(KEY, JSON.stringify(v)); },
  clear() { localStorage.removeItem(KEY); },
};

export class ApiError extends Error {
  constructor(status, message, details) { super(message); this.status = status; this.details = details; }
}

let refreshing = null;
async function refresh() {
  const a = auth.get();
  if (!a?.refreshToken) return false;
  if (!refreshing) {
    refreshing = fetch('/api/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: a.refreshToken }),
    })
      .then(async (r) => { if (!r.ok) { auth.clear(); return false; } auth.set(await r.json()); return true; })
      .catch(() => false)
      .finally(() => { refreshing = null; });
  }
  return refreshing;
}

async function readBody(res) {
  if (res.status === 204) return null;
  const type = res.headers.get('content-type') || '';
  return type.includes('json') ? res.json() : res.text();
}

/** options: method, body (sent as JSON) or text (sent as text/plain). */
export async function api(path, { method = 'GET', body, text, retry = true } = {}) {
  const headers = {};
  const a = auth.get();
  if (a?.accessToken) headers.Authorization = 'Bearer ' + a.accessToken;
  let payload;
  if (text !== undefined) { headers['Content-Type'] = 'text/plain'; payload = text; }
  else if (body !== undefined) { headers['Content-Type'] = 'application/json'; payload = JSON.stringify(body); }

  const res = await fetch(path, { method, headers, body: payload });
  if (res.status === 401 && retry && a?.refreshToken && (await refresh())) {
    return api(path, { method, body, text, retry: false });
  }
  if (res.status === 401) {
    auth.clear();
    window.dispatchEvent(new Event('agentprobe-logout'));
  }
  const data = await readBody(res);
  if (!res.ok) {
    const msg = (data && data.message) || (typeof data === 'string' && data) || res.statusText;
    throw new ApiError(res.status, msg, data && data.details);
  }
  return data;
}

export async function signIn(email, password, register) {
  const res = await fetch('/api/auth/' + (register ? 'register' : 'login'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const data = await readBody(res);
  if (!res.ok) throw new ApiError(res.status, (data && data.message) || 'Sign in failed');
  auth.set(data);
}

/** Downloads a protected file (CSV, YAML) with the bearer token. */
export async function download(path, filename) {
  const a = auth.get();
  const res = await fetch(path, { headers: { Authorization: 'Bearer ' + (a?.accessToken || '') } });
  if (!res.ok) throw new ApiError(res.status, 'Download failed');
  const url = URL.createObjectURL(await res.blob());
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
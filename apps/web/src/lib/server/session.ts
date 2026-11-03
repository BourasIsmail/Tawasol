import 'server-only';
import { cookies } from 'next/headers';
import type { TokenResponse } from '@tawassol/shared';

export const API = process.env.API_INTERNAL_URL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';
const secure = process.env.COOKIE_SECURE ? process.env.COOKIE_SECURE === 'true' : process.env.NODE_ENV === 'production';

/** Les jetons ne quittent jamais le serveur : cookies httpOnly, illisibles par le JavaScript de la page. */
export async function setSession(t: TokenResponse): Promise<void> {
  const c = await cookies();
  c.set('tw_at', t.accessToken, { httpOnly: true, secure, sameSite: 'lax', path: '/', maxAge: t.expiresInSeconds });
  c.set('tw_rt', t.refreshToken, { httpOnly: true, secure, sameSite: 'lax', path: '/', maxAge: 60 * 60 * 24 * 30 });
}

export async function clearSession(): Promise<void> {
  const c = await cookies();
  c.delete('tw_at');
  c.delete('tw_rt');
}

// Les rafraîchissements simultanés (plusieurs requêtes d'une même page) partagent un seul appel :
// le refresh token est à usage unique, un second appel le ferait révoquer.
const inflight = new Map<string, { promise: Promise<TokenResponse | null>; expires: number }>();

async function refresh(rt: string): Promise<TokenResponse | null> {
  const now = Date.now();
  for (const [k, v] of inflight) if (v.expires < now) inflight.delete(k);
  const existing = inflight.get(rt);
  if (existing) return existing.promise;
  const promise = fetch(`${API}/api/v1/auth/refresh`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: rt }),
  }).then(async (r) => (r.ok ? ((await r.json()) as TokenResponse) : null)).catch(() => null);
  inflight.set(rt, { promise, expires: now + 15_000 });
  return promise;
}

export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const c = await cookies();
  let at = c.get('tw_at')?.value;
  const rt = c.get('tw_rt')?.value;
  const call = (token?: string) => fetch(`${API}${path}`, {
    ...init, cache: 'no-store', headers: { ...(init.headers as Record<string, string>), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
  });
  let res = await call(at);
  if (res.status === 401 && rt) {
    const t = await refresh(rt);
    if (!t) { await clearSession(); return res; }
    await setSession(t);
    at = t.accessToken;
    res = await call(at);
  }
  return res;
}

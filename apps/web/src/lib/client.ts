import type { ApiError } from '@tawassol/shared';

export class ApiRequestError extends Error {
  constructor(public readonly error: ApiError) {
    super(error.message);
  }
}

const currentLocale = () => (typeof document !== 'undefined' ? document.documentElement.lang || 'fr' : 'fr');

/** Appelle l'API via le relais du serveur Next (les jetons restent dans des cookies httpOnly). */
export async function api<T = unknown>(path: string, init: RequestInit & { json?: unknown } = {}): Promise<T> {
  const { json, headers, ...rest } = init;
  const res = await fetch(`/api/bff/${path.replace(/^\//, '')}`, {
    ...rest,
    headers: { ...(json !== undefined ? { 'Content-Type': 'application/json' } : {}), ...(headers as Record<string, string>) },
    body: json !== undefined ? JSON.stringify(json) : rest.body,
  });
  if (res.status === 401 && typeof window !== 'undefined') {
    window.location.href = `/${currentLocale()}/login`;
    throw new ApiRequestError({ status: 401, code: 'UNAUTHENTICATED', message: 'Session expirée' });
  }
  if (!res.ok) {
    throw new ApiRequestError((await res.json().catch(() => null)) ?? { status: res.status, code: 'generic', message: res.statusText });
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export async function upload(path: string, file: File): Promise<unknown> {
  const body = new FormData();
  body.append('fichier', file);
  return api(path, { method: 'POST', body });
}

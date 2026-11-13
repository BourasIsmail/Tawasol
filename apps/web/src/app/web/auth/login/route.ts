import { NextResponse } from 'next/server';
import type { ApiError, TokenResponse } from '@tawassol/shared';
import { API, setSession } from '@/lib/server/session';

export async function POST(req: Request) {
  const body = await req.text();
  const res = await fetch(`${API}/api/v1/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': req.headers.get('x-forwarded-for') ?? '' }, body,
  });
  if (!res.ok) {
    const err = (await res.json().catch(() => null)) as ApiError | null;
    return NextResponse.json(err ?? { status: res.status, code: 'generic', message: 'Erreur' }, { status: res.status });
  }
  const tokens = (await res.json()) as TokenResponse;
  await setSession(tokens);
  return NextResponse.json({ role: tokens.role, mustChangePassword: tokens.mustChangePassword });
}

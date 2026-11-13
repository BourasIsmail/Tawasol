import { NextResponse } from 'next/server';
import type { TokenResponse } from '@tawassol/shared';
import { apiFetch, setSession } from '@/lib/server/session';

/** Le changement renvoie de nouveaux jetons (anciens révoqués) : on remplace les cookies côté serveur. */
export async function POST(req: Request) {
  const res = await apiFetch('/api/v1/auth/change-password', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: await req.text() });
  if (!res.ok) return new NextResponse(await res.text(), { status: res.status, headers: { 'Content-Type': 'application/json' } });
  const tokens = (await res.json()) as TokenResponse;
  await setSession(tokens);
  return NextResponse.json({ role: tokens.role });
}

import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';
import { API, clearSession } from '@/lib/server/session';

export async function POST() {
  const rt = (await cookies()).get('tw_rt')?.value;
  if (rt) {
    await fetch(`${API}/api/v1/auth/logout`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: rt }),
    }).catch(() => undefined);
  }
  await clearSession();
  return new NextResponse(null, { status: 204 });
}

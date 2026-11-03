import { NextRequest, NextResponse } from 'next/server';
import { apiFetch } from '@/lib/server/session';

// L'authentification passe par /api/auth/* : ces chemins ne sont jamais relayés tels quels.
const BLOCKED = /^auth\/(login|refresh|logout)$/;

async function relay(req: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  const { path } = await ctx.params;
  const target = path.join('/');
  if (BLOCKED.test(target)) return NextResponse.json({ code: 'NOT_FOUND' }, { status: 404 });
  const headers: Record<string, string> = {};
  for (const h of ['content-type', 'accept-language', 'idempotency-key']) {
    const v = req.headers.get(h);
    if (v) headers[h] = v;
  }
  const hasBody = !['GET', 'HEAD'].includes(req.method);
  const res = await apiFetch(`/api/v1/${target}${req.nextUrl.search}`, {
    method: req.method, headers, body: hasBody ? await req.arrayBuffer() : undefined,
  });
  const out = new Headers();
  for (const h of ['content-type', 'content-disposition', 'cache-control', 'x-content-type-options']) {
    const v = res.headers.get(h);
    if (v) out.set(h, v);
  }
  return new NextResponse(res.status === 204 ? null : await res.arrayBuffer(), { status: res.status, headers: out });
}

export { relay as GET, relay as POST, relay as PUT, relay as DELETE };

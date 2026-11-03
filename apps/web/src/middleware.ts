import createMiddleware from 'next-intl/middleware';
import { NextResponse, type NextRequest } from 'next/server';
import { routing } from './i18n/routing';

const intl = createMiddleware(routing);

/** Les pages hors /login exigent une session : sans cookie de rafraîchissement, retour à la connexion. */
export default function middleware(req: NextRequest) {
  const m = req.nextUrl.pathname.match(/^\/(fr|ar)(\/.*)?$/);
  if (m) {
    const rest = m[2] ?? '';
    const isPublic = ['/login', '/forgot', '/reset', '/verification'].some((x) => rest.startsWith(x));
    if (!isPublic && !req.cookies.get('tw_rt')) return NextResponse.redirect(new URL(`/${m[1]}/login`, req.url));
  }
  return intl(req);
}

export const config = { matcher: ['/((?!api|_next|.*\\..*).*)'] };

'use client';

import type { ReactNode } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect } from 'react';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { Loading } from './ui';

const TEAM = [
  { id: 'respDashboard', href: '/responsable/dashboard', mobile: true },
  { id: 'validation', href: '/responsable/validation', mobile: true },
  { id: 'staff', href: '/responsable/personnel', mobile: true },
  { id: 'notation', href: '/responsable/notation', mobile: true },
  { id: 'deplacements', href: '/deplacements' },
  { id: 'calendar', href: '/responsable/calendrier' },
  { id: 'substitute', href: '/responsable/suppleance' },
] as const;

const RH = [
  { id: 'dashboard', href: '/rh/dashboard', mobile: true },
  { id: 'requests', href: '/rh/demandes', mobile: true },
  { id: 'deplacements', href: '/deplacements' },
  { id: 'staff', href: '/rh/personnel' },
  { id: 'avancement', href: '/rh/avancement' },
  { id: 'examens', href: '/rh/examens' },
  { id: 'imports', href: '/rh/imports' },
  { id: 'lots', href: '/rh/lots' },
  { id: 'notation', href: '/rh/notation' },
  { id: 'publications', href: '/rh/publications' },
] as const;

const ADMIN = [
  { id: 'comptes', href: '/admin/comptes', mobile: true },
  { id: 'audit', href: '/admin/audit', mobile: true },
] as const;

const NAV = [
  { id: 'dashboard', href: '/dashboard', mobile: true },
  { id: 'fiche', href: '/fiche' },
  { id: 'demandes', href: '/demandes', mobile: true },
  { id: 'deplacements', href: '/deplacements' },
  { id: 'carriere', href: '/carriere' },
  { id: 'paie', href: '/paie', mobile: true },
  { id: 'note', href: '/note' },
  { id: 'reclamations', href: '/reclamations' },
  { id: 'documents', href: '/documents' },
  { id: 'notifications', href: '/notifications', mobile: true },
] as const;

interface Me { userId: string; role: string; responsable: boolean; suppleant: boolean; personnel?: { prenomFr: string; nomFr: string; prenomAr: string; nomAr: string } }

export function useMe() {
  return useQuery({ queryKey: ['me'], queryFn: () => api<Me>('me') });
}

export function AppShell({ children }: { children: ReactNode }) {
  const t = useTranslations();
  const locale = useLocale();
  const path = usePathname();
  const me = useMe();
  const unread = useQuery({ queryKey: ['notif-count'], queryFn: () => api<{ nonLues: number }>('notifications?limit=1'), refetchInterval: 60_000, enabled: !!me.data?.personnel });
  const active = (href: string) => path.startsWith(`/${locale}${href}`);

  async function logout() {
    await fetch('/api/auth/logout', { method: 'POST' });
    window.location.href = `/${locale}/login`;
  }

  const name = me.data?.personnel ? (locale === 'ar' ? `${me.data.personnel.prenomAr} ${me.data.personnel.nomAr}` : `${me.data.personnel.prenomFr} ${me.data.personnel.nomFr}`) : '';
  const count = unread.data?.nonLues ?? 0;

  const isRh = me.data?.role === 'rh';
  const isAdmin = me.data?.role === 'admin';
  const router = useRouter();
  useEffect(() => { if (isAdmin && !path.startsWith(`/${locale}/admin`)) router.replace(`/${locale}/admin/comptes`); }, [isAdmin, path, locale, router]);
  const manager = !!(me.data?.responsable || me.data?.suppleant);
  const team = TEAM.filter((n) => n.id !== 'substitute' || me.data?.responsable);
  const link = (n: { id: string; href: string }, ns: string) => (
    <Link key={n.id} href={`/${locale}${n.href}`} className={active(n.href) ? 'on' : ''} aria-current={active(n.href) ? 'page' : undefined}>
      <span>{t(`${ns}.${n.id}`)}</span>{n.id === 'notifications' && count > 0 && <b className="badge">{count}</b>}
    </Link>
  );
  const mobileItems = isAdmin
    ? ADMIN.map((n) => link(n, 'nav4'))
    : isRh
    ? [...RH.filter((n) => 'mobile' in n).map((n) => link(n, 'nav3')), <Link key="me" href={`/${locale}/dashboard`} className={path.startsWith(`/${locale}/rh`) ? '' : 'on'}><span>{t('nav2.mine')}</span>{count > 0 && <b className="badge">{count}</b>}</Link>]
    : manager
    ? [...team.filter((n) => 'mobile' in n).map((n) => link(n, 'nav2')), <Link key="me" href={`/${locale}/dashboard`} className={path.startsWith(`/${locale}/responsable`) ? '' : 'on'}><span>{t('nav2.mine')}</span>{count > 0 && <b className="badge">{count}</b>}</Link>]
    : NAV.filter((n) => 'mobile' in n).map((n) => link(n, 'nav'));
  const blocked = me.data && ((path.startsWith(`/${locale}/responsable`) && !manager) || (path.startsWith(`/${locale}/rh`) && !isRh) || (path.startsWith(`/${locale}/admin`) && !isAdmin));

  return (
    <div className="app">
      <aside className="side">
        <div className="logo">{t('app.name')}</div>
        <nav aria-label="Navigation">
          {isAdmin && ADMIN.map((n) => link(n, 'nav4'))}
          {!isAdmin && isRh && <><small className="grp">{t('nav3.title')}</small>{RH.map((n) => link(n, 'nav3'))}<small className="grp">{t('nav2.mine')}</small></>}
          {!isAdmin && manager && <><small className="grp">{t('nav2.team')}</small>{team.map((n) => link(n, 'nav2'))}<small className="grp">{t('nav2.mine')}</small></>}
          {!isAdmin && NAV.map((n) => link(n, 'nav'))}
        </nav>
        <button type="button" className="logout" onClick={logout}>{t('nav.logout')}</button>
      </aside>
      <div className="col">
        <div className="tb">
          <span className="grow" />
          <Link className="lang" href={`/${locale === 'fr' ? 'ar' : 'fr'}${path.replace(/^\/(fr|ar)/, '')}`} hrefLang={locale === 'fr' ? 'ar' : 'fr'}>{locale === 'fr' ? 'العربية' : 'Français'}</Link>
          <span className="av" aria-hidden="true">{name.split(' ').map((p) => p[0]).slice(0, 2).join('')}</span>
          <b className="uname">{name}</b>
        </div>
        <main className="main">
          {me.isLoading && <Loading />}
          {me.data && !me.data.personnel && !isAdmin && <p className="ban">{t('nav.noAgent')}</p>}
          {blocked && <p className="ban bad" role="alert">{t('errors.ACCESS_DENIED')}</p>}
          {((me.data?.personnel && !isAdmin) || (isAdmin && path.startsWith(`/${locale}/admin`))) && !blocked && children}
        </main>
      </div>
      <nav className="bt" aria-label="Navigation">{mobileItems}</nav>
    </div>
  );
}

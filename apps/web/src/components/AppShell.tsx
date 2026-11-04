'use client';

import { useEffect, type ReactNode } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { Icon } from './Icon';
import { Loading } from './ui';

/** Entrées de navigation de la maquette, par profil : même ordre, mêmes icônes, mêmes libellés. m = présent dans la barre basse du téléphone. */
export interface Item { id: string; icon: string; href: string; m?: boolean; sep?: boolean }

const AGENT: Item[] = [
  { id: 'dashboard', icon: 'home', href: '/dashboard', m: true },
  { id: 'fiche', icon: 'user', href: '/fiche' },
  { id: 'demandes', icon: 'file', href: '/demandes', m: true },
  { id: 'missions', icon: 'plane', href: '/deplacements' },
  { id: 'paie', icon: 'wallet', href: '/paie', m: true },
  { id: 'note', icon: 'star', href: '/note' },
  { id: 'reclamations', icon: 'alert', href: '/reclamations' },
  { id: 'documents', icon: 'book', href: '/documents' },
  { id: 'notifications', icon: 'bell', href: '/notifications', m: true },
];

const RESP: Item[] = [
  { id: 'rDash', icon: 'home', href: '/responsable/dashboard', m: true },
  { id: 'validation', icon: 'inbox', href: '/responsable/validation', m: true },
  { id: 'staff', icon: 'tree', href: '/responsable/personnel', m: true },
  { id: 'notation', icon: 'star', href: '/responsable/notation', m: true },
  { id: 'calendar', icon: 'cal', href: '/responsable/calendrier' },
  { id: 'substitute', icon: 'users', href: '/responsable/suppleance' },
  { id: 'rMissions', icon: 'plane', href: '/deplacements' },
  { id: 'mySpace', icon: 'user', href: '/dashboard', sep: true },
];

const RH: Item[] = [
  { id: 'hrDash', icon: 'home', href: '/rh/dashboard', m: true },
  { id: 'hrRequests', icon: 'inbox', href: '/rh/demandes', m: true },
  { id: 'hrStaff', icon: 'users', href: '/rh/personnel' },
  { id: 'imports', icon: 'up', href: '/rh/imports' },
  { id: 'hrMissions', icon: 'plane', href: '/deplacements' },
  { id: 'cell', icon: 'plane', href: '/deplacements?statut=validee_responsable' },
  { id: 'pay', icon: 'wallet', href: '/deplacements?statut=finalisee' },
  { id: 'career', icon: 'tree', href: '/rh/avancement' },
  { id: 'hrNotation', icon: 'star', href: '/rh/notation' },
  { id: 'hrPub', icon: 'book', href: '/rh/publications' },
  { id: 'hrDocs', icon: 'file', href: '/rh/demandes?statut=approuvee' },
  { id: 'reports', icon: 'chart', href: '/deplacements/analytique' },
  { id: 'mySpace', icon: 'user', href: '/dashboard', sep: true },
];

const ADMIN: Item[] = [
  { id: 'accounts', icon: 'users', href: '/admin/comptes', m: true },
  { id: 'roles', icon: 'tree', href: '/admin/perimetres', m: true },
  { id: 'refs', icon: 'list', href: '/admin/referentiels', m: true },
  { id: 'params', icon: 'settings', href: '/admin/parametres', m: true },
  { id: 'audit', icon: 'shield', href: '/admin/audit' },
];

const AGENT_PATHS = ['/dashboard', '/fiche', '/demandes', '/paie', '/note', '/reclamations', '/documents', '/notifications', '/carriere', '/plus'];

interface Me { userId: string; role: string; responsable: boolean; suppleant: boolean; personnel?: { prenomFr: string; nomFr: string; prenomAr: string; nomAr: string } }

export function useMe() {
  return useQuery({ queryKey: ['me'], queryFn: () => api<Me>('me') });
}

/** Liste de navigation du profil courant (utilisée par la barre latérale, la barre basse et la page « Plus »). */
export function useNav(): { items: Item[]; space: 'agent' | 'responsable' | 'rh' | 'admin' } {
  const locale = useLocale();
  const path = usePathname().replace(new RegExp(`^/${locale}`), '') || '/';
  const me = useMe();
  const role = me.data?.role;
  const manager = !!(me.data?.responsable || me.data?.suppleant);
  let space: 'agent' | 'responsable' | 'rh' | 'admin' = 'agent';
  if (role === 'admin') space = 'admin';
  else if (path.startsWith('/rh')) space = 'rh';
  else if (path.startsWith('/responsable')) space = 'responsable';
  else if (AGENT_PATHS.some((p) => path === p || path.startsWith(`${p}/`))) space = 'agent';
  else if (role === 'rh') space = 'rh';
  else if (manager) space = 'responsable';
  const items = space === 'admin' ? ADMIN : space === 'rh' ? RH : space === 'responsable' ? RESP : AGENT;
  return { items, space };
}

export function AppShell({ children }: { children: ReactNode }) {
  const t = useTranslations();
  const locale = useLocale();
  const pathFull = usePathname();
  const path = pathFull.replace(new RegExp(`^/${locale}`), '') || '/';
  const search = useSearchParams();
  const router = useRouter();
  const me = useMe();
  const { items, space } = useNav();
  const unread = useQuery({ queryKey: ['notif-count'], queryFn: () => api<{ nonLues: number }>('notifications?limit=1'), refetchInterval: 60_000, enabled: !!me.data?.personnel });
  const isRh = me.data?.role === 'rh';
  const isAdmin = me.data?.role === 'admin';
  const manager = !!(me.data?.responsable || me.data?.suppleant);

  useEffect(() => { if (isAdmin && !path.startsWith('/admin')) router.replace(`/${locale}/admin/comptes`); }, [isAdmin, path, locale, router]);

  async function logout() {
    await fetch('/api/auth/logout', { method: 'POST' });
    window.location.href = `/${locale}/login`;
  }

  const name = me.data?.personnel ? (locale === 'ar' ? `${me.data.personnel.prenomAr} ${me.data.personnel.nomAr}` : `${me.data.personnel.prenomFr} ${me.data.personnel.nomFr}`) : isAdmin ? t('nv.role_admin') : '';
  const initials = name.split(' ').map((p) => p[0]).slice(0, 2).join('');
  const count = unread.data?.nonLues ?? 0;

  /** Actif : même chemin ; pour les entrées qui partagent un chemin (filtres de statut), le paramètre statut départage. */
  const isOn = (it: Item): boolean => {
    if (it.id === 'hrRequests' && (path === '/rh/conges' || path.startsWith('/rh/conges/'))) return true;
    if (it.id === 'career') return ['/rh/avancement', '/rh/grades', '/rh/examens'].some((x) => path === x || path.startsWith(`${x}/`));
    const [hrefPath, query] = it.href.split('?');
    if (path !== hrefPath && !path.startsWith(`${hrefPath}/`)) return false;
    const wanted = query ? new URLSearchParams(query).get('statut') : null;
    const current = search.get('statut');
    const rivals = items.filter((x) => x !== it && x.href.split('?')[0] === hrefPath);
    if (wanted) return current === wanted;
    if (rivals.length) return !rivals.some((r) => new URLSearchParams(r.href.split('?')[1] ?? '').get('statut') === current && current);
    return true;
  };

  const label = (it: Item) => t(`nv.${it.id}`);
  const mobile = items.filter((i) => i.m).slice(0, 4);
  const chip = isAdmin ? t('nv.role_admin') : space === 'rh' ? t('nv.role_rh') : space === 'responsable' ? t('nv.role_resp') : isRh || manager ? t('nv.role_agent') : t('nv.role_agent');
  const rib = space === 'admin' ? 'rib_ad' : space === 'rh' ? 'rib_rh' : space === 'responsable' ? 'rib_rs' : 'rib_ag';
  const blocked = me.data && ((path.startsWith('/responsable') && !manager) || (path.startsWith('/rh') && !isRh) || (path.startsWith('/admin') && !isAdmin));
  const canShow = (me.data?.personnel && !isAdmin) || (isAdmin && path.startsWith('/admin'));
  // un responsable ou la RH qui consulte son espace agent garde un moyen de revenir à son espace de travail
  const back = space === 'agent' && (isRh || manager) && path !== '/plus' ? { href: isRh ? '/rh/dashboard' : '/responsable/dashboard', text: isRh ? t('nv.backToRh') : t('nv.backToResp') } : null;

  return (
    <div className="app">
      <aside className="side">
        <div className="logo">
          <svg width="26" height="26" viewBox="0 0 38 38" aria-hidden="true"><circle cx="15" cy="19" r="11" fill="#fff" opacity=".9" /><circle cx="24" cy="19" r="11" fill="none" stroke="#fff" strokeWidth="3" /></svg>
          <span>{t('app.name')}</span>
        </div>
        <nav aria-label="Navigation" style={{ display: 'contents' }}>
          {items.map((it) => (
            <span key={it.id} style={{ display: 'contents' }}>
              {it.sep && <hr />}
              <Link href={`/${locale}${it.href}`} className={isOn(it) ? 'on' : ''} aria-current={isOn(it) ? 'page' : undefined}>
                <Icon name={it.icon} /><span>{label(it)}</span>{it.id === 'notifications' && count > 0 && <b className="badge">{count}</b>}
              </Link>
            </span>
          ))}
          {back && <Link href={`/${locale}${back.href}`}><Icon name="chev" className="chev" /><span>{back.text}</span></Link>}
        </nav>
        <button type="button" className="logout" onClick={logout}>{t('nav.logout')}</button>
      </aside>
      <div className="col">
        <div className="tb">
          <div className="s"><Icon name="search" /><span>{t('nv.search')}</span></div>
          <span className="grow" />
          <div className="u">
            <span className="pill">{chip}</span>
            <Link href={`/${locale}/notifications`} className="bell" aria-label={t('nav.notifications')}><Icon name="bell" />{count > 0 && <b className="badge">{count}</b>}</Link>
            <div className="av" aria-hidden="true">{initials}</div>
            <b className="uname">{name}</b>
            <Link className="lang" href={`/${locale === 'fr' ? 'ar' : 'fr'}${path}`} hrefLang={locale === 'fr' ? 'ar' : 'fr'}>{locale === 'fr' ? 'العربية' : 'Français'}</Link>
          </div>
        </div>
        <main className="main">
          <div className="rib"><Icon name="lock" />{t(`nv.${rib}`)}</div>
          {me.isLoading && <Loading />}
          {me.data && !me.data.personnel && !isAdmin && <p className="ban">{t('nav.noAgent')}</p>}
          {blocked && <p className="ban bad" role="alert">{t('errors.ACCESS_DENIED')}</p>}
          {canShow && !blocked && children}
        </main>
      </div>
      <nav className="bt" aria-label="Navigation">
        {mobile.map((it) => (
          <Link key={it.id} href={`/${locale}${it.href}`} className={isOn(it) ? 'on' : ''}><Icon name={it.icon} /><span>{label(it)}</span>{it.id === 'notifications' && count > 0 && <b className="badge">{count}</b>}</Link>
        ))}
        <Link href={`/${locale}/plus`} className={path === '/plus' ? 'on' : ''}><Icon name="menu" /><span>{t('nv.more')}</span></Link>
      </nav>
    </div>
  );
}

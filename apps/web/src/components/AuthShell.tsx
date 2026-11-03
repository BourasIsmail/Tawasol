'use client';

import type { ReactNode } from 'react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { usePathname } from 'next/navigation';

/**
 * Écran d'authentification : formulaire d'un côté, panneau bleu (#0C4797) de l'autre, bascule FR/AR en contrôle segmenté.
 * Le formulaire vient en premier dans le DOM : en arabe (RTL) il passe à droite, comme sur la maquette.
 */
export function AuthShell({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  const t = useTranslations();
  const locale = useLocale();
  const path = usePathname();
  const rest = path.replace(/^\/(fr|ar)/, '');
  return (
    <main className="auth">
      <section className="auth-form">
        <div className="auth-box">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img className="auth-logo" src="/logo-entraide.png" alt="Royaume du Maroc, Entraide Nationale" />
          <header>
            <h1>{title}</h1>
            <p className="muted" style={{ margin: '4px 0 0' }}>{subtitle ?? t('app.tagline')}</p>
          </header>
          {children}
          <nav className="seg" aria-label="Langue">
            <Link href={`/fr${rest}`} hrefLang="fr" aria-current={locale === 'fr'}>{t('language.fr')}</Link>
            <Link href={`/ar${rest}`} hrefLang="ar" aria-current={locale === 'ar'}>{t('language.ar')}</Link>
          </nav>
        </div>
      </section>
      <aside className="auth-panel" aria-label={t('app.name')}>
        <div className="logo" style={{ fontWeight: 700, letterSpacing: '.08em', fontSize: 18 }}>{t('app.name')}</div>
        <h2>{t('shell.panelTitle')}</h2>
        <ol>
          {(['1', '2', '3'] as const).map((n) => (
            <li key={n}><span aria-hidden="true">{n}</span><div><b>{t(`shell.p${n}t`)}</b><small>{t(`shell.p${n}d`)}</small></div></li>
          ))}
        </ol>
        <hr />
        <div className="foot">{t('shell.foot')}</div>
      </aside>
    </main>
  );
}

'use client';

import type { ReactNode } from 'react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { usePathname } from 'next/navigation';

/**
 * Écrans publics de la maquette (connexion, mot de passe oublié, premier mot de passe) : une carte centrée sur fond neutre.
 * « brand » ajoute l'en-tête TAWASSOL · تواصل de l'écran de connexion ; sinon le titre est dans la carte.
 */
export function AuthShell({ title, subtitle, brand, children }: { title?: string; subtitle?: string; brand?: boolean; children: ReactNode }) {
  const t = useTranslations();
  const locale = useLocale();
  const path = usePathname().replace(/^\/(fr|ar)/, '');
  return (
    <main className="login-main">
      <div style={{ width: 'min(400px,100%)', display: 'flex', flexDirection: 'column', gap: 16, alignItems: 'center' }}>
        {brand && (
          <header style={{ textAlign: 'center' }}>
            <b style={{ fontSize: 22 }}>TAWASSOL · تواصل</b>
            <div className="muted">{subtitle ?? t('app.tagline')}</div>
          </header>
        )}
        <div className="login">
          {!brand && title && <b style={{ fontSize: 17 }}>{title}</b>}
          {children}
        </div>
        <nav className="lang" aria-label="Langue">
          <Link href={`/fr${path}`} hrefLang="fr" aria-current={locale === 'fr'}>{t('language.fr')}</Link>
          <Link href={`/ar${path}`} hrefLang="ar" aria-current={locale === 'ar'}>{t('language.ar')}</Link>
        </nav>
      </div>
    </main>
  );
}

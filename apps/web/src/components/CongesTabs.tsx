'use client';

import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { usePathname } from 'next/navigation';

/** Onglets de la gestion des soldes de congé (RH) : demandes à régulariser, contrôle des soldes, jours fériés. */
export function CongesTabs() {
  const t = useTranslations();
  const locale = useLocale();
  const path = usePathname().replace(new RegExp(`^/${locale}`), '');
  const tabs = [['/rh/conges', 'cg.tabReg'], ['/rh/conges/controle', 'cg.tabCheck'], ['/rh/conges/jours-feries', 'cg.tabHolidays']] as const;
  return (
    <div className="tabs" role="tablist">
      {tabs.map(([href, key]) => <Link key={href} href={`/${locale}${href}`} role="tab" aria-pressed={path === href} aria-selected={path === href}>{t(key)}</Link>)}
    </div>
  );
}

'use client';

import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { usePathname } from 'next/navigation';

/** Onglets de l'espace Carrière (maquette « Carrière ») : échelons éligibles, examens. */
export function CareerTabs() {
  const t = useTranslations();
  const locale = useLocale();
  const path = usePathname();
  const tabs = [['avancement', 'car.tabSteps'], ['examens', 'exm.title']] as const;
  return (
    <div className="tabs" role="tablist">
      {tabs.map(([seg, key]) => <Link key={seg} href={`/${locale}/rh/${seg}`} role="tab" aria-selected={path.includes(`/rh/${seg}`)} aria-pressed={path.includes(`/rh/${seg}`)}>{t(key)}</Link>)}
    </div>
  );
}

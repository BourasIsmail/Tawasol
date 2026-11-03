'use client';

import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { Icon } from '@/components/Icon';
import { useNav } from '@/components/AppShell';
import { Card, Page } from '@/components/ui';

/** Menu « Plus » de la barre basse du téléphone : les entrées de navigation qui n'y tiennent pas. */
export default function Plus() {
  const t = useTranslations();
  const locale = useLocale();
  const { items } = useNav();
  return (
    <Page title={t('nv.more')}>
      <Card flush>
        {items.filter((i) => !i.m).map((i) => (
          <Link key={i.id} className="rw link" href={`/${locale}${i.href}`}>
            <div className="rm"><b>{t(`nv.${i.id}`)}</b></div>
            <div className="rr"><Icon name="chev" className="chev" /></div>
          </Link>
        ))}
      </Card>
    </Page>
  );
}

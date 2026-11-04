'use client';

import { useTranslations } from 'next-intl';
import { CreateAccount } from '@/components/CreateAccount';
import { Page, WebOnly } from '@/components/ui';

export default function RhComptes() {
  const t = useTranslations();
  return <WebOnly><Page title={t('acc.newTitle')} sub={t('acc.rhSub')}><CreateAccount scope="rh" /></Page></WebOnly>;
}

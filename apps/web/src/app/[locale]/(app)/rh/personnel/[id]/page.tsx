'use client';

import { use } from 'react';
import { PersonnelForm } from '@/components/PersonnelForm';
import { SoldesParAnnee } from '@/components/SoldesParAnnee';
import { WebOnly } from '@/components/ui';

import { useTranslations } from 'next-intl';

export default function Fiche({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const t = useTranslations();
  return <WebOnly><PersonnelForm id={id} /><SoldesParAnnee path={`rh/conges/soldes/${id}`} title={t('cg.byYearTitle')} /></WebOnly>;
}

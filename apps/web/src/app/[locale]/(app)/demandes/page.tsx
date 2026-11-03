'use client';

import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate } from '@/lib/format';
import { Card, Empty, ErrorBox, Loading, Page, Pill, Row } from '@/components/ui';
import { DEMANDE_STATUTS } from '@tawassol/shared';

interface Demande { id: string; numero?: string; typeCode: string; statut: string; dateDebut?: string; dateFin?: string; objet: string }

export default function Demandes() {
  const t = useTranslations();
  const locale = useLocale();
  const [statut, setStatut] = useState('');
  const q = useQuery({ queryKey: ['demandes', statut], queryFn: () => api<{ content: Demande[] }>(`demandes?size=50${statut ? `&statut=${statut}` : ''}`) });
  return (
    <Page title={t('dem.title')} actions={<Link className="btn pri" href={`/${locale}/demandes/nouvelle`}>{t('dem.new')}</Link>}>
      <div className="chips" role="group">
        <button type="button" className="chip" aria-pressed={statut === ''} onClick={() => setStatut('')}>{t('dem.all')}</button>
        {DEMANDE_STATUTS.map((s) => <button type="button" key={s} className="chip" aria-pressed={statut === s} onClick={() => setStatut(s)}>{t(`status.${s}`)}</button>)}
      </div>
      {q.isLoading && <Loading />}
      {q.error && <ErrorBox error={q.error} onRetry={() => q.refetch()} />}
      {q.data && (q.data.content.length === 0
        ? <Card><Empty title={t('dem.empty')} hint={t('dem.emptyHint')} /></Card>
        : <Card flush>{q.data.content.map((d) => (
            <Row key={d.id} href={`/${locale}/demandes/${d.id}`} main={t(`dem.t_${d.typeCode}`)}
              sub={`${d.numero ?? '—'}${d.dateDebut ? ` · ${fmtDate(locale, d.dateDebut)} → ${fmtDate(locale, d.dateFin)}` : ''}`}
              right={<Pill status={d.statut} label={t(`status.${d.statut}`)} />} />
          ))}</Card>)}
    </Page>
  );
}

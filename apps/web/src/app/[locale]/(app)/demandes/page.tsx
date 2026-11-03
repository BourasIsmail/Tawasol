'use client';

import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate } from '@/lib/format';
import { Icon } from '@/components/Icon';
import { Card, Empty, ErrorBox, Loading, Page, Pill, Row } from '@/components/ui';

interface Demande { id: string; numero?: string; typeCode: string; statut: string; dateDebut?: string; dateFin?: string; objet: string; donnees?: { amount?: number } }

/** Filtres de la maquette : Toutes, Brouillons, En cours, Terminées. */
const GROUPS: Record<string, string[] | null> = { all: null, draft: ['brouillon'], progress: ['soumise', 'validee_responsable'], done: ['approuvee', 'rejetee', 'rejetee_responsable', 'annulee'] };

export default function Demandes() {
  const t = useTranslations();
  const locale = useLocale();
  const [groupe, setGroupe] = useState('all');
  const q = useQuery({ queryKey: ['demandes', 'liste'], queryFn: () => api<{ content: Demande[] }>('demandes?size=100') });
  const rows = (q.data?.content ?? []).filter((d) => !GROUPS[groupe] || GROUPS[groupe]!.includes(d.statut));
  const detail = (d: Demande) => (d.dateDebut ? ` · ${fmtDate(locale, d.dateDebut).slice(0, 5)} → ${fmtDate(locale, d.dateFin).slice(0, 5)}` : d.donnees?.amount ? ` · ${d.donnees.amount} ${t('common.mad')}` : '');
  return (
    <Page title={t('dem.title')} actions={<Link className="btn pri" href={`/${locale}/demandes/nouvelle`}><Icon name="plus" />{t('dem.new')}</Link>}>
      <div className="chips" role="group">
        {Object.keys(GROUPS).map((g) => <button type="button" key={g} className="chip" aria-pressed={groupe === g} onClick={() => setGroupe(g)}>{t(`dem.g_${g}`)}</button>)}
      </div>
      {q.isLoading && <Loading />}
      {q.error && <ErrorBox error={q.error} onRetry={() => q.refetch()} />}
      {q.data && (rows.length === 0
        ? <Card><Empty title={t('dem.empty')} hint={t('dem.emptyHint')} /></Card>
        : <Card flush>{rows.map((d) => (
            <Row key={d.id} href={`/${locale}/demandes/${d.id}`} main={`${t(`dem.t_${d.typeCode}`)}${detail(d)}`}
              sub={d.numero ? <span className="mono">{d.numero}</span> : t('dem.notSubmitted')} right={<Pill status={d.statut} label={t(`status.${d.statut}`)} />} />
          ))}</Card>)}
    </Page>
  );
}

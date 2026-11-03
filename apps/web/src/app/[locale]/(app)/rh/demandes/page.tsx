'use client';

import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate } from '@/lib/format';
import { Card, Empty, ErrorBox, Loading, Page, Row } from '@/components/ui';

interface Item { id: string; numero?: string; typeLibelle: string; statut: string; circuit: string; dateDebut?: string; dateFin?: string; ancienneteJours: number; horsDelai: boolean; nomFr: string; prenomFr: string; nomAr: string; prenomAr: string }
const TABS = [['a_traiter', 'toProcess'], ['approuvee', 'approved'], ['rejetee', 'rejected']] as const;

export default function RhDemandes() {
  const t = useTranslations();
  const locale = useLocale();
  const [tab, setTab] = useState<string>('a_traiter');
  const q = useQuery({ queryKey: ['rhqueue', tab], queryFn: () => api<{ content: Item[] }>(`rh/demandes?statut=${tab}&size=50`) });
  const nm = (x: Item) => (locale === 'ar' ? `${x.prenomAr} ${x.nomAr}` : `${x.prenomFr} ${x.nomFr}`);
  return (
    <Page title={t('rhreq.title')}>
      <div className="tabs" role="tablist">{TABS.map(([k, m]) => <button key={k} type="button" role="tab" aria-pressed={tab === k} onClick={() => setTab(k)}>{t(`rhreq.${m}`)}</button>)}</div>
      {q.isLoading && <Loading />}
      {q.error && <ErrorBox error={q.error} onRetry={() => q.refetch()} />}
      {q.data && (q.data.content.length === 0 ? <Card><Empty title={t('rhreq.empty')} /></Card> : (
        <Card flush>{q.data.content.map((x) => (
          <Row key={x.id} href={`/${locale}/rh/demandes/${x.id}`} main={`${nm(x)} · ${x.typeLibelle}`}
            sub={`${x.circuit === 'responsable' ? t('rhreq.circuitResp') : t('rhreq.circuitDirect')}${x.dateDebut ? ` · ${fmtDate(locale, x.dateDebut)} → ${fmtDate(locale, x.dateFin)}` : ''}`}
            right={tab === 'a_traiter' ? <span className={`pill ${x.horsDelai ? 'bad' : 'ok'}`}>{x.horsDelai ? '⚠ ' : '◷ '}{x.ancienneteJours} {t('common.days', { n: '' }).trim()}</span> : undefined} />
        ))}</Card>
      ))}
    </Page>
  );
}

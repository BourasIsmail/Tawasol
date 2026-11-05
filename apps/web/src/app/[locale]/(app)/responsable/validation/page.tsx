'use client';

import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate } from '@/lib/format';
import { Icon } from '@/components/Icon';
import { Card, Empty, ErrorBox, Loading, Page, Pill, Row } from '@/components/ui';

interface Item { id: string; numero?: string; typeCode: string; typeLibelle: string; statut: string; dateDebut?: string; dateFin?: string; nbJours?: number; ancienneteJours: number; nomFr: string; prenomFr: string; nomAr: string; prenomAr: string }
const TABS = ['soumise', 'validee_responsable', 'rejetee_responsable'] as const;
const FAMILLES: Record<string, string[] | null> = { all: null, conge: ['CONGE_ANNUEL', 'CONGE_MALADIE', 'CONGE_EXCEPTION', 'CONGE_PERMISSION_ABSENCE', 'CONGE_MATERNITE', 'CONGE_SANS_SOLDE', 'CONGE_ACCIDENT_TRAVAIL'], att: ['ATTESTATION'], form: ['FORMATION'], mis: ['ORDRE_MISSION'] };

export default function Validation() {
  const t = useTranslations();
  const locale = useLocale();
  const qc = useQueryClient();
  const [tab, setTab] = useState<(typeof TABS)[number]>('soumise');
  const [fam, setFam] = useState('all');
  const q = useQuery({ queryKey: ['rqueue', tab], queryFn: () => api<{ content: Item[] }>(`validation/demandes?statut=${tab}&size=50`) });
  const ok = useMutation({ mutationFn: (id: string) => api(`validation/demandes/${id}/valider`, { method: 'POST', json: {} }), onSuccess: () => { qc.invalidateQueries({ queryKey: ['rqueue'] }); qc.invalidateQueries({ queryKey: ['rdash'] }); } });
  const nm = (x: Item) => (locale === 'ar' ? `${x.prenomAr} ${x.nomAr}` : `${x.prenomFr} ${x.nomFr}`);
  return (
    <Page title={t('rval.title')} sub={t('rval.sub')}>
      <div className="chips" role="group">{Object.keys(FAMILLES).map((f) => <button type="button" key={f} className="chip" aria-pressed={fam === f} onClick={() => setFam(f)}>{t(`rq.f_${f}`)}</button>)}</div>
      <div className="tabs" role="tablist">{TABS.map((s) => <button key={s} type="button" role="tab" aria-pressed={tab === s} onClick={() => setTab(s)}>{t(`rval.${s}`)}</button>)}</div>
      {q.isLoading && <Loading />}
      {q.error && <ErrorBox error={q.error} onRetry={() => q.refetch()} />}
      {ok.error && <ErrorBox error={ok.error} />}
      {q.data && (q.data.content.filter((x) => !FAMILLES[fam] || FAMILLES[fam]!.includes(x.typeCode)).length === 0 ? <Card><Empty title={t('rval.empty')} /></Card> : (
        <Card flush>{q.data.content.filter((x) => !FAMILLES[fam] || FAMILLES[fam]!.includes(x.typeCode)).map((x) => (
          <Row key={x.id} href={`/${locale}/responsable/validation/${x.id}`} main={`${nm(x)} · ${x.typeLibelle}`}
            sub={`${x.dateDebut ? `${fmtDate(locale, x.dateDebut)} → ${fmtDate(locale, x.dateFin)} · ` : ''}${t('rval.waiting', { n: x.ancienneteJours })}`}
            right={<><Pill status={x.statut} label={t(`status.${x.statut}`)} />{tab === 'soumise' && <button type="button" className="btn ok sm" disabled={ok.isPending} onClick={(e) => { e.preventDefault(); ok.mutate(x.id); }}><Icon name="check" />{t('rval.validate')}</button>}</>} />
        ))}</Card>
      ))}
      <div className="ban"><Icon name="shield" /><div><b>{t('rq.sepTitle')}</b><small>{t('rq.sepText')}</small></div></div>
    </Page>
  );
}

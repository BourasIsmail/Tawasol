'use client';

import { useLocale, useTranslations } from 'next-intl';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { Icon } from '@/components/Icon';
import { Card, ErrorBox, Loading, Page } from '@/components/ui';

interface D { aTraiter: number; horsDelai: number; effectif: number; dossiersIncomplets: number; reclamationsOuvertes: number; lotsEnBrouillon: number; notesASoumettre: number; demandesParStatut: { statut: string; n: number }[] }

export default function RhDashboard() {
  const t = useTranslations();
  const locale = useLocale();
  const q = useQuery({ queryKey: ['rhdash'], queryFn: () => api<D>('rh/tableau-de-bord') });
  const adv = useQuery({ queryKey: ['rh-adv-count'], queryFn: () => api<{ propositions: unknown[] }>('rh/avancement/propositions') });
  const reg = useQuery({ queryKey: ['cg-reg-count'], queryFn: () => api<{ total: number }>('rh/conges/a-regulariser?size=1') });
  const camps = useQuery({ queryKey: ['rhcamps'], queryFn: () => api<{ annee: number }[]>('rh/notation/campagnes') });
  const annee = camps.data?.[0]?.annee;
  const taux = useQuery({ queryKey: ['rhtaux', annee], queryFn: () => api<{ structureId: string; nomFr: string; nomAr: string; agents: number; notes: number }[]>(`rh/notation/${annee}/taux`), enabled: !!annee });
  if (q.isLoading) return <Loading />;
  if (q.error) return <ErrorBox error={q.error} onRetry={() => q.refetch()} />;
  const d = q.data!;
  const max = Math.max(1, ...d.demandesParStatut.map((x) => Number(x.n)));
  return (
    <Page title={t('rhdash.title')}>
      <div className="g4">
        <div className="stat"><span>{t('rhdash.toProcess')}</span><b style={{ color: d.aTraiter ? 'var(--warn)' : undefined }}>{d.aTraiter}</b>{d.horsDelai > 0 && <small>{t('rhdash.lateCount', { n: d.horsDelai })}</small>}</div>
        <div className="stat"><span>{t('rhdash.headcount')}</span><b>{d.effectif}</b></div>
        <div className="stat"><span>{t('rhdash.toPromote')}</span><b>{adv.data ? adv.data.propositions.length : '—'}</b></div>
        <div className="stat"><span>{t('rhdash.incomplete')}</span><b style={{ color: d.dossiersIncomplets ? 'var(--bad)' : undefined }}>{d.dossiersIncomplets}</b></div>
      </div>
      <div className="g2">
        <Card title={t('rhdash.byStatus')}>
          <div className="bars">{d.demandesParStatut.map((x) => <div key={x.statut}><span>{t(`status.${x.statut}`)}</span><div className="meter"><i style={{ width: `${(100 * Number(x.n)) / max}%` }} /></div><b>{x.n}</b></div>)}</div>
        </Card>
        <Card title={t('rhdash.rate')}>
          {taux.data && taux.data.length > 0 ? <div className="bars">{[...taux.data].sort((x, y) => y.agents - x.agents).slice(0, 6).map((x) => { const p = x.agents ? Math.round((100 * Number(x.notes)) / x.agents) : 0; return <div key={x.structureId}><span>{locale === 'ar' ? x.nomAr : x.nomFr}</span><div className="meter"><i style={{ width: `${p}%` }} /></div><b>{p}%</b></div>; })}</div> : <p className="muted" style={{ margin: 0 }}>{t('rhdash.noCampaign')}</p>}
        </Card>
      </div>
        <Card title={t('rhdash.alerts')} flush>
          {adv.data && adv.data.propositions.length > 0 && <div className="rw"><div className="rm"><b>{t('rhdash.eligible', { n: adv.data.propositions.length })}</b><small>{t('rhdash.eligibleHint')}</small></div><div className="rr"><a className="btn sm" href={`/${locale}/rh/avancement`}>{t('rq.see')}</a></div></div>}
          <div className="rw"><div className="rm"><b>{t('rhdash.incompleteCount', { n: d.dossiersIncomplets })}</b></div><div className="rr"><a className="btn sm" href={`/${locale}/rh/personnel`}>{t('rq.see')}</a></div></div>
          {reg.data && reg.data.total > 0 && <div className="rw"><div className="rm"><b>{t('cg.alert', { n: reg.data.total })}</b><small>{t('cg.alertHint')}</small></div><div className="rr"><a className="btn sm" href={`/${locale}/rh/conges`}>{t('rq.see')}</a></div></div>}
          <a className="rw link" href={`/${locale}/rh/demandes`}><div className="rm"><b>{t('rhdash.toProcess')}</b></div><div className="rr"><b>{d.aTraiter}</b></div></a>
          <a className="rw link" href={`/${locale}/rh/lots`}><div className="rm"><b>{t('rhdash.draftLots')}</b></div><div className="rr"><b>{d.lotsEnBrouillon}</b></div></a>
          <a className="rw link" href={`/${locale}/rh/notation`}><div className="rm"><b>{t('rhdash.notesToCheck')}</b></div><div className="rr"><b>{d.notesASoumettre}</b></div></a>
          <a className="rw link" href={`/${locale}/rh/publications`}><div className="rm"><b>{t('rhdash.claims')}</b></div><div className="rr"><b>{d.reclamationsOuvertes}</b></div></a>
        </Card>
    </Page>
  );
}

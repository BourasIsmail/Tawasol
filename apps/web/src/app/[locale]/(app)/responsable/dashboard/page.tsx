'use client';

import { useLocale, useTranslations } from 'next-intl';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate } from '@/lib/format';
import { Icon } from '@/components/Icon';
import { Card, Empty, ErrorBox, Loading, Page, Row } from '@/components/ui';

interface Dash { aValider: number; effectif: number; structure?: string; delaiMoyenJours?: number | null; absentsAujourdhui: { id: string; nomFr: string; prenomFr: string; nomAr: string; prenomAr: string; dateFin: string; genre: string }[] }
interface Q { content: { id: string; typeLibelle: string; nomFr: string; prenomFr: string; nomAr: string; prenomAr: string; dateDebut?: string; dateFin?: string }[] }

export default function RespDashboard() {
  const t = useTranslations();
  const locale = useLocale();
  const d = useQuery({ queryKey: ['rdash'], queryFn: () => api<Dash>('validation/tableau-de-bord') });
  const q = useQuery({ queryKey: ['rqueue', 'top'], queryFn: () => api<Q>('validation/demandes?size=3') });
  const camp = useQuery({ queryKey: ['campagne'], queryFn: () => api<{ annee: number } | null>('notation/campagne').catch(() => null) });
  const nota = useQuery({ queryKey: ['rnota', camp.data?.annee], queryFn: () => api<{ total: number; soumises: number }>(`notation/${camp.data!.annee}/agents`), enabled: !!camp.data });
  if (d.isLoading) return <Loading />;
  if (d.error) return <ErrorBox error={d.error} onRetry={() => d.refetch()} />;
  const nm = (x: { nomFr: string; prenomFr: string; nomAr: string; prenomAr: string }) => (locale === 'ar' ? `${x.prenomAr} ${x.nomAr}` : `${x.prenomFr} ${x.nomFr}`);
  return (
    <Page title={t('rdash.title')} sub={`${d.data!.structure ? `${d.data!.structure} · ` : ''}${t('rdash.agents', { n: d.data!.effectif })}`}>
      <div className="g4">
        <div className="stat"><span>{t('rdash.toValidate')}</span><b style={{ color: d.data!.aValider ? 'var(--warn)' : undefined }}>{d.data!.aValider}</b></div>
        <div className="stat"><span>{t('rdash.absentToday')}</span><b>{d.data!.absentsAujourdhui.length}</b><small>{(() => { const c = d.data!.absentsAujourdhui.filter((x) => x.genre === 'conge').length, m = d.data!.absentsAujourdhui.filter((x) => x.genre === 'mission').length; return `${t('rdash.nConge', { n: c })} · ${t('rdash.nMission', { n: m })}`; })()}</small></div>
        <div className="stat"><span>{t('rdash.notationLeft')}</span><b>{nota.data ? nota.data.total - nota.data.soumises : '—'}</b>{nota.data && <small>{t('rdash.onAgents', { n: nota.data.total })}</small>}</div>
        <div className="stat"><span>{t('rq.delay')}</span><b>{d.data!.delaiMoyenJours != null ? `${String(d.data!.delaiMoyenJours).replace('.', ',')} j` : '—'}</b><small>{t('rq.delayHint')}</small></div>
      </div>
      <div className="g2">
        <Card title={t('rval.soumise')} flush>
          {q.data?.content.length ? q.data.content.map((x) => <Row key={x.id} main={`${nm(x)} · ${x.typeLibelle}`} sub={x.dateDebut ? `${fmtDate(locale, x.dateDebut).slice(0, 5)} → ${fmtDate(locale, x.dateFin).slice(0, 5)}` : '—'} right={<Link className="btn sm" href={`/${locale}/responsable/validation/${x.id}`}>{t('rq.see')}</Link>} />) : <Empty title={t('rval.empty')} />}
        </Card>
        <Card title={t('rdash.absentToday')} flush>
          {d.data!.absentsAujourdhui.length ? d.data!.absentsAujourdhui.map((x) => <Row key={x.id} main={nm(x)} sub={t('rdash.until', { date: fmtDate(locale, x.dateFin).slice(0, 5) })} right={<span className={`pill ${x.genre === 'mission' ? 'ok' : x.genre === 'absence' ? 'warn' : 'i'}`}><Icon name={x.genre === 'mission' ? 'plane' : x.genre === 'absence' ? 'alert' : 'cal'} />{t(`rdash.${x.genre}`)}</span>} />) : <Empty title={t('rdash.noAbsent')} />}
        </Card>
      </div>
    </Page>
  );
}

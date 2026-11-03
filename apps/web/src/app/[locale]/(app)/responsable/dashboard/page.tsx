'use client';

import { useLocale, useTranslations } from 'next-intl';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate } from '@/lib/format';
import { Card, Empty, ErrorBox, Loading, Page, Row } from '@/components/ui';

interface Dash { aValider: number; effectif: number; absentsAujourdhui: { id: string; nomFr: string; prenomFr: string; nomAr: string; prenomAr: string; dateFin: string; genre: string }[] }
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
    <Page title={t('rdash.title')}>
      <div className="g4">
        <div className="stat"><span>{t('rdash.toValidate')}</span><b>{d.data!.aValider}</b></div>
        <div className="stat"><span>{t('rdash.staff')}</span><b>{d.data!.effectif}</b></div>
        <div className="stat"><span>{t('rdash.absentToday')}</span><b>{d.data!.absentsAujourdhui.length}</b></div>
        <div className="stat"><span>{t('rdash.notationLeft')}</span><b>{nota.data ? nota.data.total - nota.data.soumises : '—'}</b></div>
      </div>
      <div className="g2">
        <Card title={t('rval.soumise')} flush>
          {q.data?.content.length ? q.data.content.map((x) => <Row key={x.id} href={`/${locale}/responsable/validation/${x.id}`} main={`${nm(x)} · ${x.typeLibelle}`} sub={x.dateDebut ? `${fmtDate(locale, x.dateDebut)} → ${fmtDate(locale, x.dateFin)}` : undefined} />) : <Empty title={t('rval.empty')} />}
        </Card>
        <Card title={t('rdash.absentToday')} flush>
          {d.data!.absentsAujourdhui.length ? d.data!.absentsAujourdhui.map((x) => <Row key={x.id} main={nm(x)} sub={t('rdash.until', { date: fmtDate(locale, x.dateFin) })} right={<span className="pill i">{t(`rdash.${x.genre}`)}</span>} />) : <Empty title={t('rdash.noAbsent')} />}
        </Card>
      </div>
      <div className="acts"><Link className="btn pri" href={`/${locale}/responsable/validation`}>{t('nav2.validation')}</Link></div>
    </Page>
  );
}

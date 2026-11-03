'use client';

import { useLocale, useTranslations } from 'next-intl';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate } from '@/lib/format';
import { Icon } from '@/components/Icon';
import { Card, Empty, ErrorBox, Loading, Page, Pill, Row } from '@/components/ui';

interface Camp { annee: number; dateOuverture: string; dateCloture: string; baremeMax: number }
interface A { id: string; nomFr: string; prenomFr: string; nomAr: string; prenomAr: string; statut: string; note?: number }

export default function Notation() {
  const t = useTranslations();
  const locale = useLocale();
  const camp = useQuery({ queryKey: ['campagne'], queryFn: () => api<Camp | null>('notation/campagne') });
  const list = useQuery({ queryKey: ['rnota', camp.data?.annee], queryFn: () => api<{ total: number; soumises: number; agents: A[] }>(`notation/${camp.data!.annee}/agents`), enabled: !!camp.data });
  if (camp.isLoading) return <Loading />;
  if (camp.error) return <ErrorBox error={camp.error} onRetry={() => camp.refetch()} />;
  if (!camp.data) return <Page title={t('rnota.title')}><Card><Empty title={t('rnota.noCampaign')} /></Card></Page>;
  const c = camp.data;
  const nm = (x: A) => (locale === 'ar' ? `${x.prenomAr} ${x.nomAr}` : `${x.prenomFr} ${x.nomFr}`);
  const l = list.data;
  return (
    <Page title={`${t('rnota.title')} ${c.annee}`} sub={t('rnota.campaign', { year: c.annee, from: fmtDate(locale, c.dateOuverture), to: fmtDate(locale, c.dateCloture) })}>
      {l && <div className="panel"><div className="cb"><div className="rw" style={{ padding: 0, border: 0 }}><div className="rm"><b>{t('rnota.avancement')}</b></div><div className="rr mono">{l.soumises} / {l.total}</div></div><div className="meter" style={{ height: 8, borderRadius: 99, background: 'var(--neu-soft)', overflow: 'hidden' }}><div style={{ width: `${l.total ? (100 * l.soumises) / l.total : 0}%`, height: '100%', background: 'var(--brand)' }} /></div></div></div>}
      {list.isLoading && <Loading />}
      {list.error && <ErrorBox error={list.error} onRetry={() => list.refetch()} />}
      {l && (l.agents.length === 0 ? <Card><Empty title={t('rnota.empty')} /></Card> : (
        <Card flush>{l.agents.map((a) => (
          <Row key={a.id} href={`/${locale}/responsable/notation/${a.id}`} main={nm(a)} sub={a.note != null ? `${a.note} / ${c.baremeMax} · ${t(`rnota.${a.statut}`)}` : t(`rnota.${a.statut}`)}
            right={a.statut === 'a_saisir' ? <span className="btn sm pri">{t('rnota.enter')}</span> : a.statut === 'brouillon' ? <span className="btn sm">{t('rnota.continue')}</span> : <Pill status={a.statut === 'soumise' ? 'soumise' : a.statut === 'brouillon' ? 'brouillon' : 'approuvee'} label={t(`rnota.${a.statut}`)} />} />
        ))}</Card>
      ))}
      <p className="rib"><Icon name="lock" />{t('rnota.rule')}</p>
    </Page>
  );
}

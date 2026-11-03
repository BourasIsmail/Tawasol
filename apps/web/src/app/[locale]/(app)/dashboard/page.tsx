'use client';

import { useLocale, useTranslations } from 'next-intl';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate, fmtMonth } from '@/lib/format';
import { Card, Empty, ErrorBox, Loading, Page, Pill, Row } from '@/components/ui';
import { useMe } from '@/components/AppShell';

interface Dash { soldeConges: { annee: number; disponible: number }; demandesEnCours: number; dernierBulletin?: { annee: number; mois: number }; derniereNote?: { annee: number; note: number }; ficheConfirmeeLe?: string }
interface Demande { id: string; numero?: string; typeCode: string; statut: string; dateDebut?: string; dateFin?: string }
interface Annonce { id: string; titre: string; publieLe: string }

export default function Dashboard() {
  const t = useTranslations();
  const locale = useLocale();
  const me = useMe();
  const dash = useQuery({ queryKey: ['dash'], queryFn: () => api<Dash>('me/tableau-de-bord') });
  const dem = useQuery({ queryKey: ['demandes', 'recent'], queryFn: () => api<{ content: Demande[] }>('demandes?size=4') });
  const ann = useQuery({ queryKey: ['annonces'], queryFn: () => api<Annonce[]>('annonces') });
  const p = me.data?.personnel;
  const name = p ? (locale === 'ar' ? p.prenomAr : p.prenomFr) : '';
  if (dash.isLoading) return <Loading />;
  if (dash.error) return <ErrorBox error={dash.error} onRetry={() => dash.refetch()} />;
  const d = dash.data!;
  return (
    <Page title={t('dash.hello', { name })} actions={<Link className="btn pri" href={`/${locale}/demandes/nouvelle`}>{t('dash.newRequest')}</Link>}>
      {!d.ficheConfirmeeLe && (
        <div className="ban warn"><div><b>{t('dash.verifyTitle')}</b><small>{t('dash.verifyText')}</small></div><Link className="btn pri" href={`/${locale}/fiche`}>{t('dash.verify')}</Link></div>
      )}
      <div className="g4">
        <div className="stat"><span>{t('dash.balance')}</span><b>{t('common.days', { n: d.soldeConges.disponible })}</b><small>{t('dash.balanceHint', { year: d.soldeConges.annee })}</small></div>
        <div className="stat"><span>{t('dash.pending')}</span><b>{d.demandesEnCours}</b></div>
        <div className="stat"><span>{t('dash.lastPayslip')}</span><b>{d.dernierBulletin ? fmtMonth(locale, d.dernierBulletin.annee, d.dernierBulletin.mois) : '—'}</b></div>
        <div className="stat"><span>{t('dash.lastNote')}</span><b>{d.derniereNote ? `${d.derniereNote.note}/20` : '—'}</b></div>
      </div>
      <div className="g2">
        <Card title={t('dash.recent')} flush>
          {dem.data?.content.length ? dem.data.content.map((x) => (
            <Row key={x.id} href={`/${locale}/demandes/${x.id}`} main={t(`dem.t_${x.typeCode}`)} sub={x.numero ?? t('status.brouillon')} right={<Pill status={x.statut} label={t(`status.${x.statut}`)} />} />
          )) : <Empty title={t('dem.empty')} hint={t('dem.emptyHint')} />}
        </Card>
        <Card title={t('dash.announcements')} flush>
          {ann.data?.length ? ann.data.slice(0, 4).map((a) => <Row key={a.id} main={a.titre} sub={fmtDate(locale, a.publieLe)} />) : <Empty title={t('docs.empty')} />}
        </Card>
      </div>
    </Page>
  );
}

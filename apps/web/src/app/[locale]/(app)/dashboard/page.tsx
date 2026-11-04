'use client';

import { useLocale, useTranslations } from 'next-intl';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { cap, fmtDate, fmtLongDate, fmtMonthShort } from '@/lib/format';
import { Card, Empty, ErrorBox, Loading, Page, Pill, Row } from '@/components/ui';
import { Icon } from '@/components/Icon';
import { useMe } from '@/components/AppShell';

interface Dash { soldeConges: { annee: number; disponible: number; aVerifier?: boolean }; demandesEnCours: number; dernierBulletin?: { annee: number; mois: number }; derniereNote?: { annee: number; note: number }; ficheConfirmeeLe?: string }
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
  const name = p ? cap(locale === 'ar' ? p.prenomAr : p.prenomFr) : '';
  if (dash.isLoading) return <Loading />;
  if (dash.error) return <ErrorBox error={dash.error} onRetry={() => dash.refetch()} />;
  const d = dash.data!;
  const periode = (x: Demande) => (x.dateDebut ? ` · ${fmtDate(locale, x.dateDebut).slice(0, 5)} → ${fmtDate(locale, x.dateFin).slice(0, 5)}` : '');
  return (
    <Page title={t('dash.hello', { name })} sub={fmtLongDate(locale)}>
      {!d.ficheConfirmeeLe && (
        <div className="ban warn"><Icon name="alert" /><div><b>{t('dash.verifyTitle')}</b><small>{t('dash.verifyText')}</small></div><Link className="btn pri" href={`/${locale}/fiche`}>{t('dash.verify')}</Link></div>
      )}
      <div className="g4">
        <div className="stat"><span>{t('dash.balance')}</span><b>{t('common.days', { n: d.soldeConges.disponible })}</b><small>{t('dash.balanceHint2', { year: d.soldeConges.annee })}{d.soldeConges.aVerifier ? ` · ${t('dash.balanceVerify')}` : ''}</small></div>
        <div className="stat"><span>{t('dash.pending')}</span><b>{d.demandesEnCours}</b></div>
        <div className="stat"><span>{t('dash.lastPayslip')}</span><b>{d.dernierBulletin ? fmtMonthShort(locale, d.dernierBulletin.annee, d.dernierBulletin.mois) : '—'}</b>{d.dernierBulletin && <small>{t('dash.lastPayslipHint')}</small>}</div>
        <div className="stat"><span>{d.derniereNote ? t('dash.noteYear', { year: d.derniereNote.annee }) : t('dash.lastNote')}</span><b>{d.derniereNote ? `${String(d.derniereNote.note).replace('.', ',')}/20` : '—'}</b>{d.derniereNote && <small>{t('note.published')}</small>}</div>
      </div>
      <div className="acts">
        <Link className="btn pri" href={`/${locale}/demandes/nouvelle`}><Icon name="plus" />{t('dash.newRequest')}</Link>
        <Link className="btn" href={`/${locale}/paie`}><Icon name="wallet" />{t('dash.seeAll')}</Link>
      </div>
      <div className="g2">
        <Card title={t('dash.recent')} flush>
          {dem.data?.content.length ? dem.data.content.map((x) => (
            <Row key={x.id} href={`/${locale}/demandes/${x.id}`} main={`${t(`dem.t_${x.typeCode}`)}${periode(x)}`} sub={x.numero ? <span className="mono">{x.numero}</span> : t('dem.notSubmitted')} right={<Pill status={x.statut} label={t(`status.${x.statut}`)} />} />
          )) : <Empty title={t('dem.empty')} hint={t('dem.emptyHint')} />}
        </Card>
        <Card title={t('dash.announcements')} flush>
          {ann.data?.length ? ann.data.slice(0, 4).map((a) => <Row key={a.id} main={a.titre} sub={fmtDate(locale, a.publieLe)} />) : <Empty title={t('docs.empty')} />}
        </Card>
      </div>
    </Page>
  );
}

'use client';

import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate, fmtMoney, fmtMonth } from '@/lib/format';
import { Card, Empty, ErrorBox, Loading, Page, Pill, Row } from '@/components/ui';

type Tab = 'payslips' | 'bonuses' | 'credits' | 'missions';
const PATH: Record<Tab, string> = { payslips: 'me/bulletins', bonuses: 'me/primes', credits: 'me/credits', missions: 'me/ordres-mission' };
type Any = Record<string, never> & Record<string, string | number>;

export default function Paie() {
  const t = useTranslations();
  const locale = useLocale();
  const [tab, setTab] = useState<Tab>('payslips');
  const q = useQuery({ queryKey: ['paie', tab], queryFn: () => api<Any[]>(PATH[tab]) });
  const money = (n: unknown) => `${fmtMoney(locale, Number(n))} ${t('common.mad')}`;
  return (
    <Page title={t('pay.title')}>
      <div className="tabs" role="tablist">
        {(['payslips', 'bonuses', 'credits', 'missions'] as Tab[]).map((k) => <button key={k} type="button" role="tab" aria-pressed={tab === k} onClick={() => setTab(k)}>{t(`pay.${k}`)}</button>)}
      </div>
      {q.isLoading && <Loading />}
      {q.error && <ErrorBox error={q.error} onRetry={() => q.refetch()} />}
      {q.data && (q.data.length === 0 ? <Card><Empty title={t('pay.empty')} /></Card> : (
        <Card flush>
          {q.data.map((r) => {
            if (tab === 'payslips') return <Row key={r.id} main={fmtMonth(locale, Number(r.annee), Number(r.mois))} sub={`${t('pay.family')} ${money(r.allocFamiliale)} · ${t('pay.mutual')} ${money(r.retenueMutuelle)}`}
              right={<><b className="mono">{money(r.salaireNet)}</b><a className="btn sm" href={`/api/bff/me/bulletins/${r.id}/pdf`} target="_blank" rel="noopener">{t('pay.pdf')}</a></>} />;
            if (tab === 'bonuses') return <Row key={r.id} main={fmtDate(locale, String(r.datePrime))} sub={`${t('pay.gross')} ${money(r.montantBrut)} · ${t('pay.tax')} ${money(r.ir)}`} right={<b className="mono">{money(r.montantNet)}</b>} />;
            if (tab === 'credits') return <Row key={r.id} main={String(r.banque)} sub={`${t('pay.monthly')} ${money(r.mensualite)} · ${t('pay.monthsLeft')} ${r.nbMoisRestants}`} right={<b className="mono">{money(r.montantRestant)}</b>} />;
            return <Row key={r.id} main={String(r.numeroOrdm ?? r.objet)} sub={`${fmtDate(locale, String(r.dateDepart))} → ${fmtDate(locale, String(r.dateRetour))}`} right={<span className="pill n">{String(r.statut)}</span>} />;
          })}
        </Card>
      ))}
      <small className="muted">{t('pay.disclaimer')}</small>
    </Page>
  );
}

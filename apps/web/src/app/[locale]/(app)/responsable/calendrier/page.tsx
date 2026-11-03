'use client';

import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtMonth } from '@/lib/format';
import { Card, Empty, ErrorBox, Loading, Page } from '@/components/ui';

interface Cal {
  membres: { id: string; nomFr: string; prenomFr: string; nomAr: string; prenomAr: string }[];
  evenements: { personnelId: string; dateDebut: string; dateFin: string; statut: string; genre: string }[];
  feries: { date: string }[];
}
const iso = (y: number, m: number, d: number) => `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;

export default function Calendrier() {
  const t = useTranslations();
  const locale = useLocale();
  const now = new Date();
  const [ym, setYm] = useState({ y: now.getFullYear(), m: now.getMonth() + 1 });
  const days = new Date(ym.y, ym.m, 0).getDate();
  const q = useQuery({ queryKey: ['cal', ym], queryFn: () => api<Cal>(`equipe/calendrier?du=${iso(ym.y, ym.m, 1)}&au=${iso(ym.y, ym.m, days)}`) });
  const move = (delta: number) => { const d = new Date(ym.y, ym.m - 1 + delta, 1); setYm({ y: d.getFullYear(), m: d.getMonth() + 1 }); };
  const today = iso(now.getFullYear(), now.getMonth() + 1, now.getDate());
  const ferie = new Set(q.data?.feries.map((f) => f.date));
  return (
    <Page title={t('rcal.title')} sub={fmtMonth(locale, ym.y, ym.m)} actions={<><button type="button" className="btn sm" onClick={() => move(-1)}>← {t('rcal.prev')}</button><button type="button" className="btn sm" onClick={() => move(1)}>{t('rcal.next')} →</button></>}>
      {q.isLoading && <Loading />}
      {q.error && <ErrorBox error={q.error} onRetry={() => q.refetch()} />}
      {q.data && (q.data.membres.length === 0 ? <Card><Empty title={t('rcal.empty')} /></Card> : (
        <Card>
          <div className="scrollx">
            <div className="gantt" style={{ gridTemplateColumns: `140px repeat(${days}, 24px)` }}>
              <div className="n" />
              {Array.from({ length: days }, (_, i) => <div key={i} className="h">{i + 1}</div>)}
              {q.data.membres.map((m) => (
                <div key={m.id} style={{ display: 'contents' }}>
                  <div className="n">{locale === 'ar' ? m.prenomAr : m.prenomFr}</div>
                  {Array.from({ length: days }, (_, i) => {
                    const d = iso(ym.y, ym.m, i + 1);
                    const dow = new Date(ym.y, ym.m - 1, i + 1).getDay();
                    const ev = q.data!.evenements.find((e) => e.personnelId === m.id && e.dateDebut <= d && e.dateFin >= d);
                    const cls = ev ? `${ev.genre}${ev.statut === 'soumise' ? ' pend' : ''}` : ferie.has(d) ? 'fe' : dow === 0 || dow === 6 ? 'we' : '';
                    return <div key={i} className={`c ${cls} ${d === today ? 'td' : ''}`} title={ev ? t(`rcal.${ev.genre}`) : undefined} />;
                  })}
                </div>
              ))}
            </div>
          </div>
          <div className="lg"><span><i style={{ background: 'var(--info)' }} />{t('rcal.conge')}</span><span><i style={{ background: 'var(--brand)' }} />{t('rcal.mission')}</span><span><i style={{ background: 'var(--warn)' }} />{t('rcal.absence')}</span><span>▨ {t('rcal.pending')}</span><span><i style={{ background: 'var(--warn-soft)' }} />{t('rcal.holiday')}</span></div>
        </Card>
      ))}
    </Page>
  );
}

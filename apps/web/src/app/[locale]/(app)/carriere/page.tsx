'use client';

import { useLocale, useTranslations } from 'next-intl';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate } from '@/lib/format';
import { Card, Empty, ErrorBox, Loading, Page } from '@/components/ui';
import { Icon } from '@/components/Icon';

interface Evenement { id: string; type: string; ancienneValeur?: string; nouvelleValeur: string; dateEffet: string; decisionRef?: string }
export interface Examen { id: string; gradeFr?: string; gradeAr?: string; dateExamen: string; dateDepot?: string; nbPostes: number; lieu?: string; resultatsEcrit?: string; resultatsFinal?: string }

export default function Carriere() {
  const t = useTranslations();
  const locale = useLocale();
  const h = useQuery({ queryKey: ['carriere'], queryFn: () => api<Evenement[]>('me/carriere') });
  const e = useQuery({ queryKey: ['examens'], queryFn: () => api<Examen[]>('examens') });
  return (
    <Page title={t('car.title')}>
      <div className="g2">
        <Card title={t('car.history')}>
          {h.isLoading && <Loading />}
          {h.error && <ErrorBox error={h.error} onRetry={() => h.refetch()} />}
          {h.data && (h.data.length === 0 ? <Empty title={t('car.empty')} /> : (
            <ol className="tl">{h.data.map((x) => (
              <li key={x.id}><span className="d" aria-hidden="true"><Icon name="check" /></span>
                <div><b>{t(`car.e_${x.type}`)}</b><small>{x.ancienneValeur ? `${x.ancienneValeur} → ` : ''}{x.nouvelleValeur} · {fmtDate(locale, x.dateEffet)}</small>{x.decisionRef && <q>{x.decisionRef}</q>}</div></li>
            ))}</ol>
          ))}
        </Card>
        <Card title={t('car.exams')}>
          {e.data && (e.data.length === 0 ? <Empty title={t('car.noExams')} /> : e.data.map((x) => (
            <dl className="kv" key={x.id}>
              <div><dt>{locale === 'ar' ? x.gradeAr : x.gradeFr}</dt><dd>{t('car.places', { n: x.nbPostes })}</dd></div>
              <div><dt>{t('car.examDate')}</dt><dd className="mono">{fmtDate(locale, x.dateExamen)}</dd></div>
              {x.dateDepot && <div><dt>{t('car.depositDate')}</dt><dd className="mono">{fmtDate(locale, x.dateDepot)}</dd></div>}
              {x.lieu && <div><dt>{t('car.place')}</dt><dd>{x.lieu}</dd></div>}
              {x.resultatsEcrit && <div><dt>{t('car.written')}</dt><dd>{x.resultatsEcrit}</dd></div>}
              {x.resultatsFinal && <div><dt>{t('car.final')}</dt><dd>{x.resultatsFinal}</dd></div>}
            </dl>
          )))}
        </Card>
      </div>
    </Page>
  );
}

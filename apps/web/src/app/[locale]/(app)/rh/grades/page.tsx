'use client';

import { useLocale, useTranslations } from 'next-intl';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { CareerTabs } from '@/components/CareerTabs';
import { Card, Empty, ErrorBox, Loading, Page, Row, WebOnly } from '@/components/ui';

interface Grade { id: string; code: number; libelleFr: string; libelleAr: string; echelle?: number; nbEchelons: number }

/** Carrière · Grades : référentiel des grades (échelle, nombre d'échelons, code). */
export default function Grades() {
  const t = useTranslations();
  const locale = useLocale();
  const q = useQuery({ queryKey: ['rh-grades'], queryFn: () => api<Grade[]>('rh/grades') });
  return (
    <WebOnly>
      <Page title={t('nv.career')}>
        <CareerTabs />
        {q.isLoading && <Loading />}
        {q.error && <ErrorBox error={q.error} onRetry={() => q.refetch()} />}
        {q.data && (q.data.length === 0 ? <Card><Empty title={t('car.noGrades')} /></Card> : (
          <Card flush>{q.data.map((g) => (
            <Row key={g.id} main={locale === 'ar' ? g.libelleAr : g.libelleFr} sub={t('car.gradeSub', { scale: g.echelle ?? '—', n: g.nbEchelons })} right={<span className="mono">{t('car.code')} {String(g.code).padStart(2, '0')}</span>} />
          ))}</Card>
        ))}
      </Page>
    </WebOnly>
  );
}

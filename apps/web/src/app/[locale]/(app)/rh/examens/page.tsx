'use client';

import { CareerTabs } from '@/components/CareerTabs';
import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate } from '@/lib/format';
import { Card, ErrorBox, Field, Loading, Page, Row } from '@/components/ui';
import type { Examen } from '../../carriere/page';

export default function ExamensRh() {
  const t = useTranslations();
  const locale = useLocale();
  const qc = useQueryClient();
  const l = useQuery({ queryKey: ['examens'], queryFn: () => api<Examen[]>('examens') });
  const [v, setV] = useState<Record<string, string>>({});
  const set = (k: string) => (e: { target: { value: string } }) => setV({ ...v, [k]: e.target.value });
  const save = useMutation({
    mutationFn: () => api('rh/examens', { method: 'POST', json: { dateExamen: v.date, dateDepot: v.depot || undefined, nbPostes: Number(v.postes || 0), lieu: v.lieu || undefined, details: v.details || undefined } }),
    onSuccess: () => { setV({}); qc.invalidateQueries({ queryKey: ['examens'] }); },
  });
  return (
    <Page title={t('nv.career')}>
      <CareerTabs />
      <div className="g2">
        <Card title={t('exm.new')}>
          <div className="ff c2">
            <Field label={t('car.examDate')}><input type="date" value={v.date ?? ''} onChange={set('date')} /></Field>
            <Field label={t('car.depositDate')}><input type="date" value={v.depot ?? ''} onChange={set('depot')} /></Field>
            <Field label={t('exm.places')}><input type="number" min="0" value={v.postes ?? ''} onChange={set('postes')} /></Field>
            <Field label={t('car.place')}><input value={v.lieu ?? ''} onChange={set('lieu')} /></Field>
          </div>
          <Field label={t('dem.description')}><textarea value={v.details ?? ''} onChange={set('details')} /></Field>
          {save.error != null && <ErrorBox error={save.error} />}
          <div className="acts"><button type="button" className="btn pri" disabled={!v.date || save.isPending} onClick={() => save.mutate()}>{t('exm.save')}</button></div>
        </Card>
        <Card flush>
          {l.isLoading && <Loading />}
          {l.data?.map((x) => <Row key={x.id} main={`${locale === 'ar' ? x.gradeAr ?? '' : x.gradeFr ?? ''} · ${fmtDate(locale, x.dateExamen)}`} sub={`${t('car.places', { n: x.nbPostes })}${x.lieu ? ` · ${x.lieu}` : ''}`} />)}
        </Card>
      </div>
    </Page>
  );
}

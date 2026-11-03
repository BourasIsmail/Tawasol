'use client';

import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate } from '@/lib/format';
import { Card, Empty, ErrorBox, Field, Loading, Page, Pill, Row } from '@/components/ui';

interface Rec { id: string; objet: string; statut: string; typeReclamation: string; reponse?: string; creeLe: string }

export default function Reclamations() {
  const t = useTranslations();
  const locale = useLocale();
  const qc = useQueryClient();
  const [f, setF] = useState({ objet: '', description: '' });
  const q = useQuery({ queryKey: ['reclamations'], queryFn: () => api<Rec[]>('reclamations') });
  const create = useMutation({ mutationFn: () => api('reclamations', { method: 'POST', json: f }), onSuccess: () => { setF({ objet: '', description: '' }); qc.invalidateQueries({ queryKey: ['reclamations'] }); } });
  return (
    <Page title={t('rec.title')}>
      {q.isLoading && <Loading />}
      {q.error && <ErrorBox error={q.error} onRetry={() => q.refetch()} />}
      {q.data && (q.data.length === 0 ? <Card><Empty title={t('rec.empty')} /></Card> : (
        <Card flush>{q.data.map((r) => (
          <Row key={r.id} main={r.objet} sub={`${fmtDate(locale, r.creeLe)}${r.reponse ? ` · ${t('rec.answer')} : ${r.reponse}` : ''}`} right={<Pill status={r.statut} label={t(`rec.${r.statut}`)} />} />
        ))}</Card>
      ))}
      <Card title={t('rec.new')}>
        <Field label={t('rec.object')}><input value={f.objet} onChange={(e) => setF({ ...f, objet: e.target.value })} /></Field>
        <Field label={t('rec.description')}><textarea value={f.description} onChange={(e) => setF({ ...f, description: e.target.value })} /></Field>
        {create.error && <ErrorBox error={create.error} />}
        <button type="button" className="btn pri" disabled={create.isPending || !f.objet.trim() || !f.description.trim()} onClick={() => create.mutate()}>{t('rec.submit')}</button>
      </Card>
    </Page>
  );
}

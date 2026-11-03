'use client';

import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useMutation, useQuery } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate } from '@/lib/format';
import { Card, Empty, ErrorBox, Field, Loading, Page, Row } from '@/components/ui';

interface Note { annee: number; note: number; appreciation?: string; dateValidation?: string; observationsAgent?: string }

export default function NotePage() {
  const t = useTranslations();
  const locale = useLocale();
  const [obs, setObs] = useState('');
  const q = useQuery({ queryKey: ['notes'], queryFn: () => api<Note[]>('me/notes') });
  const save = useMutation({ mutationFn: (annee: number) => api(`me/notes/${annee}/observations`, { method: 'PUT', json: { observations: obs } }) });
  if (q.isLoading) return <Loading />;
  if (q.error) return <ErrorBox error={q.error} onRetry={() => q.refetch()} />;
  const [last, ...older] = q.data ?? [];
  if (!last) return <Page title={t('note.title')}><Card><Empty title={t('note.none')} /></Card></Page>;
  return (
    <Page title={`${t('note.title')} ${last.annee}`}>
      <div className="g2">
        <Card>
          <div className="big"><b>{String(last.note).replace('.', locale === 'ar' ? '.' : ',')}</b><span className="muted"> {t('note.outOf')}</span></div>
          <dl className="kv"><div><dt>{t('note.appreciation')}</dt><dd>{last.appreciation ?? '—'}</dd></div><div><dt>{t('fiche.confirmed').slice(0, 0)}</dt><dd>{fmtDate(locale, last.dateValidation)}</dd></div></dl>
        </Card>
        <Card title={t('note.observations')}>
          <Field label={t('note.observations')} hint={t('note.obsHint')}><textarea value={obs || last.observationsAgent || ''} onChange={(e) => setObs(e.target.value)} /></Field>
          {save.error && <ErrorBox error={save.error} />}
          {save.isSuccess && <p className="okt" role="status">{t('note.sent')}</p>}
          <button type="button" className="btn pri" disabled={!obs.trim() || save.isPending} onClick={() => save.mutate(last.annee)}>{t('note.sendObs')}</button>
        </Card>
      </div>
      {older.length > 0 && <Card flush>{older.map((n) => <Row key={n.annee} main={String(n.annee)} sub={n.appreciation} right={<b className="mono">{n.note} / 20</b>} />)}</Card>}
    </Page>
  );
}

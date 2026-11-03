'use client';

import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDateTime } from '@/lib/format';
import { Card, ErrorBox, Field, Loading, Page } from '@/components/ui';

interface Ligne { id: string; action: string; entite?: string; entiteId?: string; creeLe: string; login?: string }

export default function Audit() {
  const t = useTranslations();
  const locale = useLocale();
  const [action, setAction] = useState('');
  const [page, setPage] = useState(0);
  const q = useQuery({ queryKey: ['admin-audit', action, page], queryFn: () => api<{ content: Ligne[]; total: number; size: number }>(`admin/audit?action=${encodeURIComponent(action)}&page=${page}`) });
  const total = q.data?.total ?? 0, size = q.data?.size ?? 50;
  return (
    <Page title={t('adm.auditTitle')} sub={t('adm.auditIntro')}>
      <Field label={t('adm.filter')}><input value={action} onChange={(e) => { setAction(e.target.value.toUpperCase()); setPage(0); }} /></Field>
      {q.isLoading && <Loading />}
      {q.error && <ErrorBox error={q.error} onRetry={() => q.refetch()} />}
      {q.data && (
        <Card flush>
          <table className="tbl"><thead><tr><th>{t('adm.when')}</th><th>{t('adm.by')}</th><th>{t('adm.action')}</th><th>{t('adm.object')}</th></tr></thead>
            <tbody>{q.data.content.map((l) => <tr key={l.id}><td className="mono">{fmtDateTime(locale, l.creeLe)}</td><td className="mono">{l.login ?? '—'}</td><td className="mono">{l.action}</td><td className="mono">{l.entite ?? ''} {l.entiteId ? l.entiteId.slice(0, 8) : ''}</td></tr>)}</tbody></table>
        </Card>
      )}
      <div className="acts">
        <button type="button" className="btn" disabled={page === 0} onClick={() => setPage(page - 1)}>{t('adm.prev')}</button>
        <button type="button" className="btn" disabled={(page + 1) * size >= total} onClick={() => setPage(page + 1)}>{t('adm.next')}</button>
      </div>
    </Page>
  );
}

'use client';

import { useLocale, useTranslations } from 'next-intl';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate } from '@/lib/format';
import { Card, Empty, ErrorBox, Loading, Page, Row } from '@/components/ui';

interface Doc { id: string; titre: string; type: string; publieLe?: string; expireLe?: string; accuseLecture: boolean; lu: boolean }
interface Ann { id: string; titre: string; message: string; publieLe: string }

export default function Documents() {
  const t = useTranslations();
  const locale = useLocale();
  const qc = useQueryClient();
  const docs = useQuery({ queryKey: ['documents'], queryFn: () => api<Doc[]>('documents') });
  const ann = useQuery({ queryKey: ['annonces'], queryFn: () => api<Ann[]>('annonces') });
  const ack = useMutation({ mutationFn: (id: string) => api(`documents/${id}/lecture`, { method: 'POST' }), onSuccess: () => qc.invalidateQueries({ queryKey: ['documents'] }) });
  return (
    <Page title={t('docs.title')}>
      {(docs.isLoading || ann.isLoading) && <Loading />}
      {docs.error && <ErrorBox error={docs.error} onRetry={() => docs.refetch()} />}
      <Card title={t('docs.documents')} flush>
        {docs.data?.length ? docs.data.map((d) => (
          <Row key={d.id} main={d.titre} sub={`${d.type}${d.expireLe ? ` · ${t('docs.expires', { date: fmtDate(locale, d.expireLe) })}` : ''}`}
            right={d.accuseLecture ? (d.lu ? <span className="pill ok">✓ {t('docs.read')}</span> : <button type="button" className="btn sm" onClick={() => ack.mutate(d.id)}>{t('docs.ack')}</button>) : undefined} />
        )) : <Empty title={t('docs.empty')} />}
      </Card>
      <Card title={t('docs.announcements')} flush>
        {ann.data?.length ? ann.data.map((a) => <Row key={a.id} main={a.titre} sub={`${fmtDate(locale, a.publieLe)} · ${a.message}`} />) : <Empty title={t('docs.empty')} />}
      </Card>
    </Page>
  );
}

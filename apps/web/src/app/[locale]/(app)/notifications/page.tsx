'use client';

import { useLocale, useTranslations } from 'next-intl';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDateTime } from '@/lib/format';
import { Card, Empty, ErrorBox, Loading, Page, Row } from '@/components/ui';

interface N { id: string; titre: string; message?: string; lien?: string; lu: boolean | number; creeLe: string }

export default function Notifications() {
  const t = useTranslations();
  const locale = useLocale();
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['notifications'], queryFn: () => api<{ nonLues: number; items: N[] }>('notifications') });
  const done = () => { qc.invalidateQueries({ queryKey: ['notifications'] }); qc.invalidateQueries({ queryKey: ['notif-count'] }); };
  const markAll = useMutation({ mutationFn: () => api('notifications/lu-tout', { method: 'POST' }), onSuccess: done });
  const mark = useMutation({ mutationFn: (id: string) => api(`notifications/${id}/lu`, { method: 'POST' }), onSuccess: done });
  return (
    <Page title={t('notif.title')} actions={q.data?.nonLues ? <button type="button" className="btn" onClick={() => markAll.mutate()}>{t('notif.markAll')}</button> : undefined}>
      {q.isLoading && <Loading />}
      {q.error && <ErrorBox error={q.error} onRetry={() => q.refetch()} />}
      {q.data && (q.data.items.length === 0 ? <Card><Empty title={t('notif.empty')} /></Card> : (
        <Card flush>{q.data.items.map((n) => (
          <Row key={n.id} onClick={() => !n.lu && mark.mutate(n.id)} main={n.titre} right={!n.lu ? <span className="pill info">{t('notif.new')}</span> : undefined} sub={`${n.message ?? ''} · ${fmtDateTime(locale, n.creeLe)}`} />
        ))}</Card>
      ))}
    </Page>
  );
}

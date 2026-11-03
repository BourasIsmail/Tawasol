'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { Card, ErrorBox, Loading, Page, Row } from '@/components/ui';

interface Ligne { code: string; libelle: string; actif: boolean | number; detail?: string }
const TYPES = ['types-demande', 'transports', 'programmes', 'types-document'] as const;

export default function Referentiels() {
  const t = useTranslations();
  const [type, setType] = useState<(typeof TYPES)[number]>('types-demande');
  const q = useQuery({ queryKey: ['admin-ref', type], queryFn: () => api<Ligne[]>(`admin/referentiels/${type}`) });
  return (
    <Page title={t('adm.refTitle')}>
      <div className="tabs" role="tablist">{TYPES.map((k) => <button key={k} type="button" role="tab" aria-pressed={type === k} onClick={() => setType(k)}>{t(`adm.r_${k}`)}</button>)}</div>
      {q.isLoading && <Loading />}
      {q.error && <ErrorBox error={q.error} onRetry={() => q.refetch()} />}
      {q.data && (
        <Card flush>
          {q.data.map((r) => {
            const actif = r.actif === true || r.actif === 1;
            const sub = type === 'types-demande' ? t('adm.valResp', { v: t(r.detail === 'requise' ? 'adm.required' : 'adm.optional') }) : r.detail ?? '';
            return <Row key={r.code} main={<><span className="mono">{type === 'types-document' ? '' : r.code}</span>{type === 'types-document' ? '' : ' · '}{r.libelle}</>} sub={sub} right={<span className={`pill ${actif ? 'ok' : ''}`}>{actif ? t('adm.active') : t('adm.inactive')}</span>} />;
          })}
        </Card>
      )}
    </Page>
  );
}

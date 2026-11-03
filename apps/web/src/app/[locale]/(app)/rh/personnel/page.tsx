'use client';

import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { Card, Empty, ErrorBox, Loading, Page, WebOnly } from '@/components/ui';

interface P { id: string; matricule: string; nomFr: string; prenomFr: string; nomAr: string; prenomAr: string; gradeFr?: string; gradeAr?: string; structureFr: string; structureAr: string }

export default function RhPersonnel() {
  const t = useTranslations();
  const locale = useLocale();
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const list = useQuery({ queryKey: ['rhpeople', q, page], queryFn: () => api<{ content: P[]; total: number; size: number }>(`personnel?q=${encodeURIComponent(q)}&page=${page}&size=25`) });
  const ar = locale === 'ar';
  return (
    <WebOnly>
      <Page title={t('rhstaff.title')} sub={list.data ? `${list.data.total}` : undefined} actions={<><Link className="btn" href={`/${locale}/rh/imports`}>{t('rhstaff.importExcel')}</Link><Link className="btn pri" href={`/${locale}/rh/personnel/nouveau`}>{t('rhstaff.newAgent')}</Link></>}>
        <input className="in" style={{ maxWidth: 320, border: '1px solid var(--line)', borderRadius: 8, padding: '9px 11px', background: 'var(--surface)', color: 'inherit' }} placeholder={t('rhstaff.search')} aria-label={t('rhstaff.search')} value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
        {list.isLoading && <Loading />}
        {list.error && <ErrorBox error={list.error} onRetry={() => list.refetch()} />}
        {list.data && (list.data.content.length === 0 ? <Card><Empty title={t('rhimp.noRows')} /></Card> : (
          <Card flush>
            <div className="scrollx"><table className="tbl"><thead><tr><th>{t('rhstaff.matricule')}</th><th>{t('rhstaff.nameFr')}</th><th>{t('rhstaff.grade')}</th><th>{t('rhstaff.structure')}</th><th /></tr></thead>
              <tbody>{list.data.content.map((p) => (
                <tr key={p.id}><td className="mono">{p.matricule}</td><td>{ar ? `${p.prenomAr} ${p.nomAr}` : `${p.prenomFr} ${p.nomFr}`}</td><td>{(ar ? p.gradeAr : p.gradeFr) ?? '—'}</td><td>{ar ? p.structureAr : p.structureFr}</td><td><Link className="btn sm" href={`/${locale}/rh/personnel/${p.id}`}>{t('rhstaff.edit')}</Link></td></tr>
              ))}</tbody></table></div>
          </Card>
        ))}
        <div className="acts"><button type="button" className="btn sm" disabled={page === 0} onClick={() => setPage(page - 1)}>{t('rhimp.prev')}</button><button type="button" className="btn sm" disabled={!list.data || (page + 1) * list.data.size >= list.data.total} onClick={() => setPage(page + 1)}>{t('rhimp.next')}</button></div>
      </Page>
    </WebOnly>
  );
}

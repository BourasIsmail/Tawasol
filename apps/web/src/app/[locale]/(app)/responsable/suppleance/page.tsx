'use client';

import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate } from '@/lib/format';
import { Card, Empty, ErrorBox, Field, Loading, Page, Row } from '@/components/ui';

interface Person { id: string; nomFr: string; prenomFr: string; nomAr: string; prenomAr: string }
interface S extends Person { dateDebut: string; dateFin: string; personnelId: string; actif?: boolean | number }

export default function Suppleance() {
  const t = useTranslations();
  const locale = useLocale();
  const qc = useQueryClient();
  const [f, setF] = useState({ suppleantId: '', dateDebut: '', dateFin: '' });
  const list = useQuery({ queryKey: ['supp'], queryFn: () => api<{ designes: S[]; aSupplier: S[] }>('suppleances') });
  const cand = useQuery({ queryKey: ['supp-cand'], queryFn: () => api<Person[]>('suppleances/candidats') });
  const done = () => { qc.invalidateQueries({ queryKey: ['supp'] }); qc.invalidateQueries({ queryKey: ['me'] }); };
  const create = useMutation({ mutationFn: () => api('suppleances', { method: 'POST', json: f }), onSuccess: () => { setF({ suppleantId: '', dateDebut: '', dateFin: '' }); done(); } });
  const end = useMutation({ mutationFn: (id: string) => api(`suppleances/${id}`, { method: 'DELETE' }), onSuccess: done });
  const nm = (x: Person) => (locale === 'ar' ? `${x.prenomAr} ${x.nomAr}` : `${x.prenomFr} ${x.nomFr}`);
  if (list.isLoading) return <Loading />;
  if (list.error) return <ErrorBox error={list.error} onRetry={() => list.refetch()} />;
  return (
    <Page title={t('rsub.title')} sub={t('rsub.intro')}>
      <Card>
        <div className="ff c2">
          <Field label={t('rsub.substitute')}><select value={f.suppleantId} onChange={(e) => setF({ ...f, suppleantId: e.target.value })}><option value="" />{cand.data?.map((p) => <option key={p.id} value={p.id}>{nm(p)}</option>)}</select></Field>
          <span />
          <Field label={t('rsub.from')}><input type="date" value={f.dateDebut} onChange={(e) => setF({ ...f, dateDebut: e.target.value })} /></Field>
          <Field label={t('rsub.to')}><input type="date" value={f.dateFin} onChange={(e) => setF({ ...f, dateFin: e.target.value })} /></Field>
        </div>
        {create.error && <ErrorBox error={create.error} />}
        <button type="button" className="btn pri" disabled={!f.suppleantId || !f.dateDebut || !f.dateFin || create.isPending} onClick={() => create.mutate()}>{t('rsub.save')}</button>
        <p className="muted" style={{ margin: 0 }}>{t('rsub.rules')}</p>
      </Card>
      <Card title={t('rsub.mine')} flush>
        {list.data!.designes.length ? list.data!.designes.map((s) => <Row key={s.id} main={nm(s)} sub={`${fmtDate(locale, s.dateDebut)} → ${fmtDate(locale, s.dateFin)}`} right={s.actif !== false ? <button type="button" className="btn sm" onClick={() => end.mutate(s.id)}>{t('rsub.end')}</button> : undefined} />) : <Empty title={t('rsub.none')} />}
      </Card>
      {list.data!.aSupplier.length > 0 && (
        <Card title={t('rsub.asSub')} flush>{list.data!.aSupplier.map((s) => <Row key={s.id} main={nm(s)} sub={`${fmtDate(locale, s.dateDebut)} → ${fmtDate(locale, s.dateFin)}`} />)}</Card>
      )}
    </Page>
  );
}

'use client';

import { use, useEffect, useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import Link from 'next/link';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { Card, ErrorBox, Field, Loading, Page } from '@/components/ui';
import { Icon } from '@/components/Icon';

interface Camp { annee: number; baremeMax: number }
interface D { id: string; nomFr: string; prenomFr: string; nomAr: string; prenomAr: string; statut: string; note?: number; appreciation?: string; observations?: string }

export default function Saisie({ params }: { params: Promise<{ pid: string }> }) {
  const { pid } = use(params);
  const t = useTranslations();
  const locale = useLocale();
  const qc = useQueryClient();
  const camp = useQuery({ queryKey: ['campagne'], queryFn: () => api<Camp | null>('notation/campagne') });
  const annee = camp.data?.annee;
  const q = useQuery({ queryKey: ['rnota-d', annee, pid], queryFn: () => api<D>(`notation/${annee}/agents/${pid}`), enabled: !!annee });
  const [f, setF] = useState({ note: '', appreciation: '', observations: '' });
  useEffect(() => { if (q.data) setF({ note: q.data.note != null ? String(q.data.note) : '', appreciation: q.data.appreciation ?? '', observations: q.data.observations ?? '' }); }, [q.data]);
  const after = () => { qc.invalidateQueries({ queryKey: ['rnota'] }); qc.invalidateQueries({ queryKey: ['rnota-d'] }); };
  const save = useMutation({ mutationFn: () => api(`notation/${annee}/agents/${pid}`, { method: 'PUT', json: { note: Number(f.note), appreciation: f.appreciation || null, observations: f.observations || null } }), onSuccess: after });
  const submit = useMutation({ mutationFn: async () => { await save.mutateAsync(); return api(`notation/${annee}/agents/${pid}/soumettre`, { method: 'POST' }); }, onSuccess: after });
  if (camp.isLoading || q.isLoading) return <Loading />;
  if (q.error) return <ErrorBox error={q.error} onRetry={() => q.refetch()} />;
  if (!camp.data || !q.data) return null;
  const d = q.data;
  const locked = !['a_saisir', 'brouillon'].includes(d.statut);
  const name = locale === 'ar' ? `${d.prenomAr} ${d.nomAr}` : `${d.prenomFr} ${d.nomFr}`;
  const invalid = f.note === '' || Number.isNaN(Number(f.note)) || Number(f.note) < 0 || Number(f.note) > camp.data.baremeMax;
  return (
    <Page title={t('rnota.noteOf', { name })} sub={t('rnota.score', { max: camp.data.baremeMax })}>
      {locked && <div className="ban"><div><b>{t('rnota.locked')}</b></div></div>}
      <Card>
        <div className="ff c2">
          <Field label={t('rnota.score', { max: camp.data.baremeMax })}><input type="number" step="0.25" min="0" max={camp.data.baremeMax} value={f.note} disabled={locked} onChange={(e) => setF({ ...f, note: e.target.value })} /></Field>
          <Field label={t('rnota.appreciation')}>
            <input list="appr" value={f.appreciation} disabled={locked} onChange={(e) => setF({ ...f, appreciation: e.target.value })} />
            <datalist id="appr">{['Très bien', 'Bien', 'Assez bien', 'Passable', 'Insuffisant'].map((a) => <option key={a} value={a} />)}</datalist>
          </Field>
        </div>
        <Field label={t('rnota.observations')}><textarea value={f.observations} disabled={locked} onChange={(e) => setF({ ...f, observations: e.target.value })} /></Field>
        {(save.error || submit.error) && <ErrorBox error={save.error ?? submit.error} />}
        {save.isSuccess && !submit.isSuccess && <p className="okt" role="status">{t('rnota.saved')}</p>}
        {submit.isSuccess && <p className="okt" role="status">{t('rnota.submittedMsg')}</p>}
        {!locked && (
          <div className="acts">
            <button type="button" className="btn" disabled={invalid || save.isPending} onClick={() => save.mutate()}>{t('rnota.saveDraft')}</button>
            <button type="button" className="btn pri" disabled={invalid || submit.isPending} onClick={() => submit.mutate()}>{t('rnota.submit')}</button>
          </div>
        )}
      </Card>
      <p className="rib"><Icon name="lock" />{t('rnota.rule')}</p>
      <Link className="muted" href={`/${locale}/responsable/notation`}>← {t('common.back')}</Link>
    </Page>
  );
}

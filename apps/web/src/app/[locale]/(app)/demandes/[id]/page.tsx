'use client';

import { Suspense, use } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useRouter, useSearchParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate, fmtDateTime } from '@/lib/format';
import { Chaine, type EtapeChaine } from '@/components/Chaine';
import { Card, ErrorBox, errorMessage, Loading, Page, Pill } from '@/components/ui';

interface Hist { ancienStatut?: string; nouveauStatut: string; commentaire?: string; acteur?: string; creeLe: string }
interface Piece { id: string; nomFichier: string; tailleKb?: number }
interface Demande { id: string; numero?: string; typeCode: string; objet: string; description?: string; statut: string; dateDebut?: string; dateFin?: string; nbJours?: number; historique: Hist[]; piecesJointes: Piece[]; avertissements?: string[]; chaine?: EtapeChaine[] }

function Detail({ id }: { id: string }) {
  const t = useTranslations();
  const locale = useLocale();
  const router = useRouter();
  const qc = useQueryClient();
  const err = useSearchParams().get('err');
  const q = useQuery({ queryKey: ['demande', id], queryFn: () => api<Demande>(`demandes/${id}`) });
  const refresh = () => { qc.invalidateQueries({ queryKey: ['demande', id] }); qc.invalidateQueries({ queryKey: ['demandes'] }); qc.invalidateQueries({ queryKey: ['dash'] }); };
  const submit = useMutation({ mutationFn: () => api(`demandes/${id}/soumettre`, { method: 'POST' }), onSuccess: () => { router.replace(`/${locale}/demandes/${id}`); refresh(); } });
  const cancel = useMutation({ mutationFn: () => api(`demandes/${id}/annuler`, { method: 'POST', json: {} }), onSuccess: refresh });
  const del = useMutation({ mutationFn: () => api(`demandes/${id}`, { method: 'DELETE' }), onSuccess: () => router.push(`/${locale}/demandes`) });
  async function open(pid: string) {
    const { url } = await api<{ url: string }>(`demandes/${id}/pieces-jointes/${pid}/lien`);
    window.open(`/api/bff${url.replace('/api/v1', '')}`, '_blank', 'noopener');
  }
  if (q.isLoading) return <Loading />;
  if (q.error) return <ErrorBox error={q.error} onRetry={() => q.refetch()} />;
  const d = q.data!;
  const cancellable = ['brouillon', 'soumise', 'validee_responsable'].includes(d.statut);
  return (
    <Page title={t(`dem.t_${d.typeCode}`)} sub={d.numero ? <span className="mono">{d.numero}</span> : t('status.brouillon')} actions={<Pill status={d.statut} label={t(`status.${d.statut}`)} />}>
      {err && <div className="ban bad" role="alert"><div><b>{t.has(`errors.${err}`) ? t(`errors.${err}`) : t('errors.generic')}</b></div></div>}
      {d.avertissements?.includes('DEBT_CAPACITY_NOT_ASSESSED') && <div className="ban warn"><div><small>{t('dem.warnDebt')}</small></div></div>}
      <div className="g2">
        {d.chaine && d.chaine.length > 0 && <Card title={t('chain.title')}><Chaine etapes={d.chaine} /></Card>}
        <Card title={t('dem.history')}>
          <ol className="tl">
            {d.historique.map((h, i) => (
              <li key={i}>
                <span className="d" aria-hidden="true">✓</span>
                <div><b>{t(`status.${h.nouveauStatut}`)}</b><small>{h.acteur ? `${h.acteur} · ` : ''}{fmtDateTime(locale, h.creeLe)}</small>{h.commentaire && <q>{h.commentaire}</q>}</div>
              </li>
            ))}
          </ol>
        </Card>
        <Card title={t('dem.details')}>
          <dl className="kv">
            {d.dateDebut && <div><dt>{t('dem.period')}</dt><dd className="mono">{fmtDate(locale, d.dateDebut)} → {fmtDate(locale, d.dateFin)}</dd></div>}
            {d.nbJours != null && <div><dt>{t('dem.workdays')}</dt><dd>{d.nbJours}</dd></div>}
            <div><dt>{t('dem.object')}</dt><dd>{d.objet}</dd></div>
            <div><dt>{t('dem.attachments')}</dt><dd>{d.piecesJointes.length ? d.piecesJointes.map((p) => <button key={p.id} type="button" className="linkbtn" onClick={() => open(p.id)}>{p.nomFichier}</button>) : t('common.none')}</dd></div>
          </dl>
          {d.typeCode === 'ATTESTATION' && d.statut === 'approuvee' && (
            <div className="acts"><a className="btn pri" href={`/api/bff/demandes/${id}/attestation`} download>{t('dem.attestationPdf')}</a></div>
          )}
          {(submit.error || cancel.error) && <ErrorBox error={submit.error ?? cancel.error} />}
          <div className="acts">
            {d.statut === 'brouillon' && <button type="button" className="btn pri" disabled={submit.isPending} onClick={() => submit.mutate()}>{t('dem.submit')}</button>}
            {d.statut === 'brouillon' && <button type="button" className="btn bad" onClick={() => del.mutate()}>{t('dem.deleteDraft')}</button>}
            {cancellable && d.statut !== 'brouillon' && <button type="button" className="btn bad" disabled={cancel.isPending} onClick={() => cancel.mutate()}>{t('dem.cancel')}</button>}
          </div>
          {cancellable && d.statut !== 'brouillon' && <small className="muted">{t('dem.cancelHint')}</small>}
        </Card>
      </div>
    </Page>
  );
}

export default function Page_({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return <Suspense fallback={<Loading />}><Detail id={id} /></Suspense>;
}

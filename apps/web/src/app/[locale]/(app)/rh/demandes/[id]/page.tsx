'use client';

import { use, useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate, fmtDateTime } from '@/lib/format';
import { Card, ErrorBox, Field, Loading, Page, Pill } from '@/components/ui';

interface D {
  id: string; numero?: string; typeCode: string; typeLibelle: string; statut: string; objet: string; description?: string; dateDebut?: string; dateFin?: string; nbJours?: number;
  commentaireResponsable?: string; traitable: boolean;
  agent: { matricule: string; nomFr: string; prenomFr: string; nomAr: string; prenomAr: string; gradeFr?: string; structureFr: string; structureAr: string };
  solde?: { disponible: number; suffisant: boolean };
  historique: { nouveauStatut: string; commentaire?: string; acteur?: string; creeLe: string }[];
  piecesJointes: { id: string; nomFichier: string }[];
}

export default function RhDemande({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const t = useTranslations();
  const locale = useLocale();
  const qc = useQueryClient();
  const [comment, setComment] = useState('');
  const [mode, setMode] = useState<'idle' | 'reject'>('idle');
  const [missing, setMissing] = useState(false);
  const q = useQuery({ queryKey: ['rhdetail', id], queryFn: () => api<D>(`rh/demandes/${id}`) });
  const done = () => { qc.invalidateQueries({ queryKey: ['rhdetail', id] }); qc.invalidateQueries({ queryKey: ['rhqueue'] }); qc.invalidateQueries({ queryKey: ['rhdash'] }); setMode('idle'); };
  const decide = useMutation({ mutationFn: (ok: boolean) => api(`rh/demandes/${id}/${ok ? 'approuver' : 'rejeter'}`, { method: 'POST', json: { commentaire: comment } }), onSuccess: done });
  if (q.isLoading) return <Loading />;
  if (q.error) return <ErrorBox error={q.error} onRetry={() => q.refetch()} />;
  const d = q.data!;
  const nm = locale === 'ar' ? `${d.agent.prenomAr} ${d.agent.nomAr}` : `${d.agent.prenomFr} ${d.agent.nomFr}`;
  async function open(pid: string) {
    const { url } = await api<{ url: string }>(`demandes/${id}/pieces-jointes/${pid}/lien`).catch(() => ({ url: '' }));
    if (url) window.open(`/api/bff${url.replace('/api/v1', '')}`, '_blank', 'noopener');
  }
  return (
    <Page title={`${d.typeLibelle} · ${nm}`} sub={d.numero ? <span className="mono">{d.numero}</span> : undefined} actions={<Pill status={d.statut} label={t(`status.${d.statut}`)} />}>
      {decide.isSuccess && <div className="ban ok" role="status"><div><b>{t('rhreq.done')}</b></div></div>}
      <div className="g2">
        <Card title={t('dem.details')}>
          <dl className="kv">
            <div><dt>{t('rval.agent')}</dt><dd>{nm} <span className="mono">({d.agent.matricule})</span></dd></div>
            <div><dt>{t('fiche.structure')}</dt><dd>{locale === 'ar' ? d.agent.structureAr : d.agent.structureFr}</dd></div>
            {d.dateDebut && <div><dt>{t('dem.period')}</dt><dd className="mono">{fmtDate(locale, d.dateDebut)} → {fmtDate(locale, d.dateFin)}</dd></div>}
            {d.nbJours != null && <div><dt>{t('dem.workdays')}</dt><dd>{d.nbJours}</dd></div>}
            {d.solde && <div><dt>{t('rval.balance')}</dt><dd>{d.solde.disponible} · <span className={`pill ${d.solde.suffisant ? 'ok' : 'bad'}`}>{d.solde.suffisant ? `✓ ${t('rval.balanceOk')}` : `✕ ${t('rval.balanceShort')}`}</span></dd></div>}
            <div><dt>{t('dem.object')}</dt><dd>{d.objet}</dd></div>
            {d.description && <div><dt>{t('dem.description')}</dt><dd>{d.description}</dd></div>}
            {d.commentaireResponsable && <div><dt>{t('rhreq.managerComment')}</dt><dd>{d.commentaireResponsable}</dd></div>}
            <div><dt>{t('dem.attachments')}</dt><dd>{d.piecesJointes.length ? d.piecesJointes.map((p) => <button key={p.id} type="button" className="linkbtn" onClick={() => open(p.id)}>{p.nomFichier}</button>) : t('common.none')}</dd></div>
          </dl>
          {d.typeCode === 'ORDRE_MISSION' && <p className="rib">{t('rhreq.missionNote')}</p>}
        </Card>
        <Card title={t('dem.history')}>
          <ol className="tl">{d.historique.map((h, i) => <li key={i}><span className="d" aria-hidden="true">✓</span><div><b>{t(`status.${h.nouveauStatut}`)}</b><small>{h.acteur ? `${h.acteur} · ` : ''}{fmtDateTime(locale, h.creeLe)}</small>{h.commentaire && <q>{h.commentaire}</q>}</div></li>)}</ol>
        </Card>
      </div>
      {!d.traitable && !['approuvee', 'rejetee'].includes(d.statut) && <div className="ban warn"><div><small>{t('rhreq.notProcessable')}</small></div></div>}
      {d.traitable && mode === 'idle' && (
        <Card>
          <Field label={t('rhreq.comment')}><textarea value={comment} onChange={(e) => setComment(e.target.value)} /></Field>
          <div className="acts">
            <button type="button" className="btn pri" disabled={decide.isPending || d.typeCode === 'ORDRE_MISSION'} onClick={() => decide.mutate(true)}>✓ {t('rhreq.approve')}</button>
            <button type="button" className="btn bad" onClick={() => { if (!comment.trim()) { setMissing(true); return; } setMissing(false); decide.mutate(false); }}>✕ {t('rhreq.reject')}</button>
          </div>
          {missing && <p className="error" role="alert">{t('rval.commentRequired')}</p>}
          {decide.error && <ErrorBox error={decide.error} />}
        </Card>
      )}
    </Page>
  );
}

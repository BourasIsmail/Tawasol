'use client';

import { use, useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate, fmtDateTime } from '@/lib/format';
import { Chaine, type EtapeChaine } from '@/components/Chaine';
import { Icon } from '@/components/Icon';
import { Card, ErrorBox, Field, Loading, Page, Pill } from '@/components/ui';

interface Person { id: string; nomFr: string; prenomFr: string; nomAr: string; prenomAr: string }
interface Detail {
  id: string; numero?: string; typeCode: string; typeLibelle: string; statut: string; objet: string; description?: string; dateDebut?: string; dateFin?: string; nbJours?: number;
  agent: Person & { matricule: string; gradeFr?: string; gradeAr?: string; structureFr: string; structureAr: string };
  solde?: { disponible: number; suffisant: boolean };
  equipe: (Person & { dateDebut: string; dateFin: string; genre: string; statut: string })[];
  historique: { nouveauStatut: string; commentaire?: string; acteur?: string; creeLe: string }[];
  nbPiecesJointes?: number;
  chaine: EtapeChaine[];
  monTourDeDecider: boolean;
}

export default function ValidationDetail({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const t = useTranslations();
  const locale = useLocale();
  const qc = useQueryClient();
  const [mode, setMode] = useState<'idle' | 'reject'>('idle');
  const [comment, setComment] = useState('');
  const [missing, setMissing] = useState(false);
  const q = useQuery({ queryKey: ['rdetail', id], queryFn: () => api<Detail>(`validation/demandes/${id}`) });
  const done = () => { qc.invalidateQueries({ queryKey: ['rdetail', id] }); qc.invalidateQueries({ queryKey: ['rqueue'] }); qc.invalidateQueries({ queryKey: ['rdash'] }); setMode('idle'); };
  const decide = useMutation({ mutationFn: (v: { ok: boolean }) => api(`validation/demandes/${id}/${v.ok ? 'valider' : 'rejeter'}`, { method: 'POST', json: { commentaire: comment } }), onSuccess: done });
  if (q.isLoading) return <Loading />;
  if (q.error) return <ErrorBox error={q.error} onRetry={() => q.refetch()} />;
  const d = q.data!;
  const nm = (x: Person) => (locale === 'ar' ? `${x.prenomAr} ${x.nomAr}` : `${x.prenomFr} ${x.nomFr}`);
  const pending = d.statut === 'soumise' && d.monTourDeDecider;
  const attend = d.statut === 'soumise' && !d.monTourDeDecider;
  function reject() {
    if (!comment.trim()) { setMissing(true); return; }
    setMissing(false);
    decide.mutate({ ok: false });
  }
  return (
    <Page title={`${d.typeLibelle} · ${nm(d.agent)}`} sub={d.numero ? <span className="mono">{d.numero}</span> : undefined} actions={<Pill status={d.statut} label={t(`status.${d.statut}`)} />}>
      {decide.isSuccess && <div className="ban ok" role="status"><div><b>{d.statut === 'validee_responsable' ? t('rval.validated') : t('rval.rejected')}</b></div></div>}
      {attend && <div className="ban warn"><div><small>{t('chain.notYet')}</small></div></div>}
      <div className="g2">
        <Card title={t('rval.request')}>
          <dl className="kv">
            <div><dt>{t('rval.agent')}</dt><dd>{nm(d.agent)} <span className="mono">({d.agent.matricule})</span></dd></div>
            {d.dateDebut && <div><dt>{t('dem.period')}</dt><dd className="mono">{fmtDate(locale, d.dateDebut)} → {fmtDate(locale, d.dateFin)}</dd></div>}
            {d.nbJours != null && <div><dt>{t('dem.workdays')}</dt><dd>{d.nbJours}</dd></div>}
            {d.solde && <div><dt>{t('rval.balance')}</dt><dd>{d.solde.disponible} · <span className={`pill ${d.solde.suffisant ? 'ok' : 'bad'}`}>{d.solde.suffisant ? t('rval.balanceOk') : t('rval.balanceShort')}</span></dd></div>}
            <div><dt>{t('dem.object')}</dt><dd>{d.objet}</dd></div>
            {d.description && <div><dt>{t('dem.description')}</dt><dd>{d.description}</dd></div>}
            {d.nbPiecesJointes != null && d.nbPiecesJointes > 0 && <div><dt>{t('dem.attachments')}</dt><dd>{t('rval.attachments', { n: d.nbPiecesJointes })}</dd></div>}
          </dl>
          <p className="rib"><Icon name="eye" />{t('rval.hiddenTitle')} : {t('rval.hiddenText')}</p>
        </Card>
        <Card title={t('rval.team')}>
          {d.equipe.length === 0 ? <p className="muted" style={{ margin: 0 }}>{t('rval.noTeam')}</p> : d.equipe.map((e, i) => (
            <div key={i} className="ban warn"><Icon name="alert" /><div><b>{nm(e)}</b><small>{t(`rdash.${e.genre}`)} · {fmtDate(locale, e.dateDebut)} → {fmtDate(locale, e.dateFin)}</small></div></div>
          ))}
        </Card>
      </div>
      <Card title={t('chain.title')}><Chaine etapes={d.chaine} /><small className="muted">{t('chain.hint')}</small></Card>
      {pending && mode === 'idle' && (
        <div className="acts">
          <button type="button" className="btn ok" disabled={decide.isPending} onClick={() => decide.mutate({ ok: true })}><Icon name="check" />{t('rval.validate')}</button>
          <button type="button" className="btn bad" onClick={() => setMode('reject')}><Icon name="x" />{t('rval.reject')}</button>
        </div>
      )}
      {pending && mode === 'reject' && (
        <Card title={t('rval.rejectReason')}>
          <Field label={t('rval.comment')}><textarea value={comment} onChange={(e) => setComment(e.target.value)} aria-invalid={missing} /></Field>
          {missing && <p className="error" role="alert">{t('rval.commentRequired')}</p>}
          <div className="acts">
            <button type="button" className="btn bad" disabled={decide.isPending} onClick={reject}>{t('rval.confirmReject')}</button>
            <button type="button" className="btn" onClick={() => { setMode('idle'); setMissing(false); }}>{t('common.cancel')}</button>
          </div>
        </Card>
      )}
      {decide.error && <ErrorBox error={decide.error} />}
      <Card title={t('dem.history')}>
        <ol className="tl">{d.historique.map((h, i) => (
          <li key={i}><span className="d" aria-hidden="true"><Icon name="check" /></span><div><b>{t(`status.${h.nouveauStatut}`)}</b><small>{h.acteur ? `${h.acteur} · ` : ''}{fmtDateTime(locale, h.creeLe)}</small>{h.commentaire && <q>{h.commentaire}</q>}</div></li>
        ))}</ol>
      </Card>
    </Page>
  );
}

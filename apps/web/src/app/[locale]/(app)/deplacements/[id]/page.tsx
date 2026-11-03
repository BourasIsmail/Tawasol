'use client';

import { use, useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate, fmtDateTime, fmtMoney } from '@/lib/format';
import { Chaine, type EtapeChaine } from '@/components/Chaine';
import { Card, ErrorBox, Field, Loading, Page, Pill } from '@/components/ui';
import { Icon } from '@/components/Icon';

interface Hist { seq: number; de?: string; vers: string; commentaire?: string; migre?: boolean; date: string; acteur?: string }
interface Mission {
  id: string; agentNom: string; matricule: string; objet: string; statut: string; numeroOrdm?: string; numeroEtat?: string; numeroOrdmLegacy?: string; numeroEtatLegacy?: string;
  dateDepart: string; dateRetour?: string; departDelegation?: string; arrivalDelegation?: string; moyenTransport?: string; programme?: string; remboursable: boolean;
  repasTaux1: number; repasTaux2: number; indemniteTaux1: number; indemniteTaux2: number; montantIndemnites: number; fraisTransport: number; distanceKm?: number; nombreTrajets?: number; tauxKm?: number; montantKm: number; montantTotal: number;
  historiqueNonReproductible: boolean; motifRejet?: string; avertissements?: string[]; historique: Hist[]; actionsPossibles: string[]; chaine?: EtapeChaine[];
}

const ENDPOINT: Record<string, string> = { soumettre: 'soumettre', valider_niveau1: 'valider-niveau1', approuver: 'approuver', finaliser: 'finaliser', payer: 'payer', annuler_paiement: 'annuler-paiement', rejeter: 'rejeter', annuler: 'annuler' };
const AVEC_MOTIF = new Set(['rejeter', 'annuler_paiement']);

export default function DetailMission({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const t = useTranslations();
  const locale = useLocale();
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['mission', id], queryFn: () => api<Mission>(`missions/${id}`) });
  const [action, setAction] = useState<string | null>(null);
  const [commentaire, setCommentaire] = useState('');
  const [reel, setReel] = useState<{ retour: string; frais: string; km: string; trajets: string }>({ retour: '', frais: '', km: '', trajets: '' });
  const run = useMutation({
    mutationFn: (a: string) => {
      const json = a === 'finaliser'
        ? { dateRetour: reel.retour ? `${reel.retour}:00` : undefined, fraisTransport: reel.frais ? Number(reel.frais) : undefined, distanceKm: reel.km ? Number(reel.km) : undefined, nombreTrajets: reel.trajets ? Number(reel.trajets) : undefined, commentaire: commentaire || undefined }
        : { commentaire: commentaire || undefined };
      return api(`missions/${id}/${ENDPOINT[a]}`, { method: 'POST', json });
    },
    onSuccess: () => { setAction(null); setCommentaire(''); qc.invalidateQueries({ queryKey: ['mission', id] }); qc.invalidateQueries({ queryKey: ['missions'] }); },
  });
  if (q.isLoading) return <Loading />;
  if (q.error) return <ErrorBox error={q.error} onRetry={() => q.refetch()} />;
  const m = q.data!;
  const doc = (type: string) => `/api/bff/missions/${id}/documents/${type}`;
  const ordre = m.numeroOrdm ?? m.numeroOrdmLegacy;
  const etat = m.numeroEtat ?? m.numeroEtatLegacy;
  const peutTelecharger = m.actionsPossibles.includes('telecharger');
  const actions = m.actionsPossibles.filter((a) => ENDPOINT[a]);
  const direct = ['soumettre', 'valider_niveau1', 'approuver', 'payer'];

  return (
    <Page title={m.agentNom} sub={<span className="mono">{m.matricule}{m.numeroOrdm ? ` · ${m.numeroOrdm}` : ''}{m.numeroEtat ? ` · ${m.numeroEtat}` : ''}</span>} actions={<Pill status={m.statut} label={t(`status.${m.statut}`)} />}>
      {m.historiqueNonReproductible && <div className="ban warn"><div><small>{t('mis.historicAmount')}</small></div></div>}
      {m.avertissements?.map((a) => <div key={a} className="ban warn"><div><small>{t.has(`mis.w_${a}`) ? t(`mis.w_${a}`) : a}</small></div></div>)}
      {m.motifRejet && <div className="ban bad"><div><b>{t('status.rejetee')}</b><small>{m.motifRejet}</small></div></div>}
      <div className="g2">
        <Card title={t('mis.details')}>
          <dl className="kv">
            <div><dt>{t('mis.object')}</dt><dd>{m.objet}</dd></div>
            <div><dt>{t('mis.depart')} → {t('mis.arrival')}</dt><dd>{m.departDelegation ?? '—'} → {m.arrivalDelegation}</dd></div>
            <div><dt>{t('mis.dateDepart')}</dt><dd className="mono">{fmtDateTime(locale, m.dateDepart)}</dd></div>
            <div><dt>{t('mis.dateRetour')}</dt><dd className="mono">{m.dateRetour ? fmtDateTime(locale, m.dateRetour) : '—'}</dd></div>
            <div><dt>{t('mis.transport')}</dt><dd>{m.moyenTransport ?? '—'}</dd></div>
            {m.programme && <div><dt>{t('mis.programme')}</dt><dd>{m.programme}</dd></div>}
            <div><dt>{t('mis.numOrdre')}</dt><dd className="mono">{ordre ?? <span className="muted">{t('mis.notYet')}</span>}{m.numeroOrdmLegacy && !m.numeroOrdm && <small> ({t('mis.legacy')})</small>}</dd></div>
            <div><dt>{t('mis.numEtat')}</dt><dd className="mono">{etat ?? <span className="muted">{t('mis.notYetEtat')}</span>}{m.numeroEtatLegacy && !m.numeroEtat && <small> ({t('mis.legacy')})</small>}</dd></div>
          </dl>
          {(m.statut === 'finalisee' || m.statut === 'payee') && (
            <>
              <h4>{t('mis.amounts')}</h4>
              <dl className="kv">
                <div><dt>{t('mis.meals1')}</dt><dd>{m.repasTaux1} × {fmtMoney(locale, m.indemniteTaux1)}</dd></div>
                {m.repasTaux2 > 0 && <div><dt>{t('mis.meals2')}</dt><dd>{m.repasTaux2} × {fmtMoney(locale, m.indemniteTaux2)}</dd></div>}
                <div><dt>{t('mis.indemnities')}</dt><dd className="mono">{fmtMoney(locale, m.montantIndemnites)}</dd></div>
                {m.fraisTransport > 0 && <div><dt>{t('mis.fees')}</dt><dd className="mono">{fmtMoney(locale, m.fraisTransport)}</dd></div>}
                {m.montantKm > 0 && <div><dt>{t('mis.km')}</dt><dd className="mono">{m.distanceKm} × {m.nombreTrajets} × {m.tauxKm} = {fmtMoney(locale, m.montantKm)}</dd></div>}
                <div><dt><b>{t('mis.total')}</b></dt><dd className="mono"><b>{fmtMoney(locale, m.montantTotal)} {t('common.mad')}</b></dd></div>
              </dl>
            </>
          )}
        </Card>
        <div>
          <Card title={t('mis.documents')}>
            {peutTelecharger ? (
              <div className="acts" style={{ flexWrap: 'wrap' }}>
                <a className="btn" href={doc('ordre')} download>{t('mis.docOrdre')}</a>
                {(m.statut === 'finalisee' || m.statut === 'payee') && <a className="btn" href={doc('etat')} download>{t('mis.docEtat')}</a>}
                {(m.statut === 'finalisee' || m.statut === 'payee') && m.montantKm > 0 && <a className="btn" href={doc('etat-km')} download>{t('mis.docEtatKm')}</a>}
              </div>
            ) : <p className="muted">{t('mis.docsHint')}</p>}
          </Card>
          {actions.length > 0 && (
            <Card>
              {run.error != null && <ErrorBox error={run.error} />}
              {action === 'finaliser' && (
                <>
                  <h4>{t('mis.finalizeTitle')}</h4>
                  <p className="muted">{t('mis.finalizeHint')}</p>
                  <div className="ff c2">
                    <Field label={t('mis.realReturn')}><input type="datetime-local" value={reel.retour} onChange={(e) => setReel({ ...reel, retour: e.target.value })} /></Field>
                    <Field label={t('mis.fraisTransport')}><input type="number" min="0" step="0.01" value={reel.frais} onChange={(e) => setReel({ ...reel, frais: e.target.value })} /></Field>
                    <Field label={t('mis.distance')}><input type="number" min="0" step="0.1" value={reel.km} onChange={(e) => setReel({ ...reel, km: e.target.value })} /></Field>
                    <Field label={t('mis.trajets')}><input type="number" min="1" value={reel.trajets} onChange={(e) => setReel({ ...reel, trajets: e.target.value })} /></Field>
                  </div>
                </>
              )}
              {action && (AVEC_MOTIF.has(action) || action === 'finaliser' || action === 'annuler') && (
                <Field label={AVEC_MOTIF.has(action) ? t('mis.reason') : t('mis.comment')}><textarea value={commentaire} onChange={(e) => setCommentaire(e.target.value)} /></Field>
              )}
              <div className="acts" style={{ flexWrap: 'wrap' }}>
                {action ? (
                  <>
                    <button type="button" className="btn pri" disabled={run.isPending || (AVEC_MOTIF.has(action) && !commentaire.trim())} onClick={() => run.mutate(action)}>{t('mis.confirm')}</button>
                    <button type="button" className="btn" onClick={() => setAction(null)}>{t('common.cancel')}</button>
                  </>
                ) : actions.map((a) => (
                  <button key={a} type="button" className={`btn ${a === 'rejeter' || a === 'annuler' || a === 'annuler_paiement' ? 'bad' : 'pri'}`} disabled={run.isPending}
                    onClick={() => (direct.includes(a) ? run.mutate(a) : setAction(a))}>{t(`mis.a_${a}`)}</button>
                ))}
              </div>
            </Card>
          )}
          {m.chaine && <Card title={t('chain.title')}><Chaine etapes={m.chaine} /></Card>}
          <Card title={t('mis.history')}>
            <ol className="tl">
              {m.historique.map((h) => (
                <li key={h.seq}><span className="d" aria-hidden="true"><Icon name="check" /></span>
                  <div><b>{t(`status.${h.vers}`)}</b><small>{h.acteur ? `${h.acteur} · ` : ''}{fmtDate(locale, h.date)}</small>{h.commentaire && <q>{h.commentaire}</q>}</div></li>
              ))}
            </ol>
          </Card>
        </div>
      </div>
    </Page>
  );
}

'use client';

import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useSearchParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate } from '@/lib/format';
import { Icon } from '@/components/Icon';
import { Card, Empty, ErrorBox, Field, Loading, Page, Pill } from '@/components/ui';

interface Item { id: string; numero?: string; typeLibelle: string; statut: string; circuit: string; dateDebut?: string; dateFin?: string; ancienneteJours: number; horsDelai: boolean; nomFr: string; prenomFr: string; nomAr: string; prenomAr: string }
interface Detail { id: string; numero?: string; typeCode: string; typeLibelle: string; statut: string; dateDebut?: string; dateFin?: string; objet: string; commentaireResponsable?: string; traitable: boolean; solde?: { disponible: number; suffisant: boolean }; agent: { nomFr: string; prenomFr: string; nomAr: string; prenomAr: string } }
const TABS = [['a_traiter', 'toProcess'], ['approuvee', 'approved'], ['rejetee', 'rejected']] as const;

/** Traitement des demandes : liste à gauche, demande sélectionnée à droite (maquette « Traitement des demandes »). */
export default function RhDemandes() {
  const t = useTranslations();
  const locale = useLocale();
  const qc = useQueryClient();
  const initial = useSearchParams().get('statut');
  const [tab, setTab] = useState<string>(TABS.some(([k]) => k === initial) ? (initial as string) : 'a_traiter');
  const [sel, setSel] = useState<string | null>(null);
  const [comment, setComment] = useState('');
  const [missing, setMissing] = useState(false);
  const q = useQuery({ queryKey: ['rhqueue', tab], queryFn: () => api<{ content: Item[] }>(`rh/demandes?statut=${tab}&size=50`) });
  const current = sel ?? q.data?.content[0]?.id ?? null;
  const d = useQuery({ queryKey: ['rhdetail', current], queryFn: () => api<Detail>(`rh/demandes/${current}`), enabled: !!current });
  const decide = useMutation({
    mutationFn: (ok: boolean) => api(`rh/demandes/${current}/${ok ? 'approuver' : 'rejeter'}`, { method: 'POST', json: { commentaire: comment } }),
    onSuccess: () => { setComment(''); setSel(null); qc.invalidateQueries({ queryKey: ['rhqueue'] }); qc.invalidateQueries({ queryKey: ['rhdetail'] }); qc.invalidateQueries({ queryKey: ['rhdash'] }); },
  });
  const nm = (x: { nomFr: string; prenomFr: string; nomAr: string; prenomAr: string }) => (locale === 'ar' ? `${x.prenomAr} ${x.nomAr}` : `${x.prenomFr} ${x.nomFr}`);
  const circuit = (c: string) => (c === 'responsable' ? <span className="pill ok"><Icon name="check" />{t('status.validee_responsable')}</span> : <span className="pill">{t('rhreq.direct')}</span>);
  const det = d.data;
  return (
    <Page title={t('rhreq.title')} actions={<a className="btn" href={`/${locale}/rh/conges`}><Icon name="cal" />{t('cg.title')}</a>}>
      <div className="tabs" role="tablist">{TABS.map(([k, m]) => <button key={k} type="button" role="tab" aria-pressed={tab === k} onClick={() => { setTab(k); setSel(null); }}>{t(`rhreq.${m}`)}</button>)}</div>
      {q.isLoading && <Loading />}
      {q.error && <ErrorBox error={q.error} onRetry={() => q.refetch()} />}
      {q.data && (q.data.content.length === 0 ? <Card><Empty title={t('rhreq.empty')} /></Card> : (
        <div className="gs">
          <Card flush>
            <div className="scroll">
              <table className="tbl"><thead><tr><th>{t('rhreq.colAgent')}</th><th>{t('rhreq.colType')}</th><th>{t('rhreq.colCircuit')}</th></tr></thead>
                <tbody>{q.data.content.map((x) => (
                  <tr key={x.id} tabIndex={0} role="button" onClick={() => setSel(x.id)} onKeyDown={(e) => e.key === 'Enter' && setSel(x.id)} style={{ cursor: 'pointer', background: x.id === current ? 'var(--brand-soft)' : undefined }}>
                    <td>{nm(x)}</td><td>{x.typeLibelle}</td><td>{circuit(x.circuit)}</td>
                  </tr>
                ))}</tbody></table>
            </div>
          </Card>
          <Card title={t('rhreq.selected')}>
            {d.isLoading && <Loading />}
            {det && (
              <>
                <b>{nm(det.agent)} · {det.typeLibelle}</b>
                <dl className="kv">
                  {det.dateDebut && <div><dt>{t('dem.period')}</dt><dd className="mono">{fmtDate(locale, det.dateDebut).slice(0, 5)} → {fmtDate(locale, det.dateFin).slice(0, 5)}</dd></div>}
                  {det.commentaireResponsable !== undefined && <div><dt>{t('rhreq.manager')}</dt><dd>{det.commentaireResponsable || t('status.validee_responsable')}</dd></div>}
                  {det.solde && <div><dt>{t('rval.balance')}</dt><dd>{det.solde.suffisant ? t('rhreq.enough') : t('rval.balanceShort')}</dd></div>}
                </dl>
                {det.traitable && (
                  <>
                    <Field label={t('rhreq.comment')}><textarea value={comment} onChange={(e) => setComment(e.target.value)} /></Field>
                    {missing && <p className="err" role="alert">{t('rval.commentRequired')}</p>}
                    {decide.error != null && <ErrorBox error={decide.error} />}
                    <div className="acts">
                      <button type="button" className="btn ok" disabled={decide.isPending || det.typeCode === 'ORDRE_MISSION'} onClick={() => decide.mutate(true)}><Icon name="check" />{t('rhreq.approve')}</button>
                      <button type="button" className="btn bad" onClick={() => { if (!comment.trim()) { setMissing(true); return; } setMissing(false); decide.mutate(false); }}><Icon name="x" />{t('rhreq.reject')}</button>
                    </div>
                  </>
                )}
                {det.typeCode === 'ATTESTATION' && det.statut === 'approuvee' && <div className="acts"><a className="btn" href={`/api/bff/demandes/${det.id}/attestation`} download><Icon name="file" />{t('rhreq.genPdf')}</a></div>}
                <div className="acts"><a className="btn sm" href={`/${locale}/rh/demandes/${det.id}`}>{t('rq.see')}</a><Pill status={det.statut} label={t(`status.${det.statut}`)} /></div>
              </>
            )}
          </Card>
        </div>
      ))}
    </Page>
  );
}

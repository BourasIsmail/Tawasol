'use client';

import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate } from '@/lib/format';
import { Card, Empty, ErrorBox, Field, Loading, Page, Pill, Row, WebOnly } from '@/components/ui';

interface Rec { id: string; objet: string; description: string; statut: string; typeReclamation: string; creeLe: string; nomFr: string; prenomFr: string; nomAr: string; prenomAr: string; matricule: string; champConcerne?: string; valeurProposee?: string }
interface Ann { id: string; titre: string; active: boolean | number; publieLe: string; expireLe?: string }
interface Doc { id: string; titre: string; type: string; publie: boolean | number }
interface Ref { id: string; code: string; nomFr?: string; nomDelegFr?: string; libelle?: string }

function Claims() {
  const t = useTranslations();
  const locale = useLocale();
  const qc = useQueryClient();
  const [open, setOpen] = useState<string | null>(null);
  const [txt, setTxt] = useState('');
  const q = useQuery({ queryKey: ['rhclaims'], queryFn: () => api<Rec[]>('rh/reclamations') });
  const done = () => { setOpen(null); setTxt(''); qc.invalidateQueries({ queryKey: ['rhclaims'] }); qc.invalidateQueries({ queryKey: ['rhdash'] }); };
  const take = useMutation({ mutationFn: (id: string) => api(`rh/reclamations/${id}/prendre`, { method: 'POST' }), onSuccess: done });
  const answer = useMutation({ mutationFn: (v: { id: string; statut: string }) => api(`rh/reclamations/${v.id}/repondre`, { method: 'POST', json: { reponse: txt, statut: v.statut } }), onSuccess: done });
  if (q.isLoading) return <Loading />;
  if (q.error) return <ErrorBox error={q.error} onRetry={() => q.refetch()} />;
  if (!q.data?.length) return <Card><Empty title={t('rhpub.noClaims')} /></Card>;
  return (
    <Card flush>{q.data.map((r) => (
      <div key={r.id}>
        <Row main={`${locale === 'ar' ? `${r.prenomAr} ${r.nomAr}` : `${r.prenomFr} ${r.nomFr}`} · ${r.objet}`} sub={`${r.description}${r.champConcerne ? ` (${r.champConcerne} → ${r.valeurProposee ?? '?'})` : ''} · ${fmtDate(locale, r.creeLe)}`}
          right={<><Pill status={r.statut} label={t(`rec.${r.statut}`)} />{r.statut === 'nouvelle' && <button type="button" className="btn sm" onClick={() => take.mutate(r.id)}>{t('rhpub.take')}</button>}{['nouvelle', 'en_cours'].includes(r.statut) && <button type="button" className="btn sm pri" onClick={() => setOpen(r.id)}>{t('rhpub.answer')}</button>}</>} />
        {open === r.id && <div className="cb"><Field label={t('rhpub.answerText')}><textarea value={txt} onChange={(e) => setTxt(e.target.value)} /></Field>
          {answer.error && <ErrorBox error={answer.error} />}
          <div className="acts"><button type="button" className="btn ok" disabled={!txt.trim()} onClick={() => answer.mutate({ id: r.id, statut: 'resolue' })}>{t('rhpub.resolve')}</button><button type="button" className="btn bad" disabled={!txt.trim()} onClick={() => answer.mutate({ id: r.id, statut: 'rejetee' })}>{t('rhpub.rejectClaim')}</button></div></div>}
      </div>
    ))}</Card>
  );
}

function Announcements() {
  const t = useTranslations();
  const locale = useLocale();
  const qc = useQueryClient();
  const [f, setF] = useState({ titre: '', message: '', cibleType: 'tous', cibleId: '', expireLe: '' });
  const list = useQuery({ queryKey: ['rhann'], queryFn: () => api<Ann[]>('rh/annonces') });
  const deleg = useQuery({ queryKey: ['rhdeleg'], queryFn: () => api<Ref[]>('rh/delegations'), enabled: f.cibleType === 'delegation' });
  const structs = useQuery({ queryKey: ['rhstructs2'], queryFn: () => api<Ref[]>('rh/structures'), enabled: f.cibleType === 'structure' });
  const create = useMutation({ mutationFn: () => api('rh/annonces', { method: 'POST', json: { titre: f.titre, message: f.message, cibleType: f.cibleType, cibleId: f.cibleId || undefined, expireLe: f.expireLe ? `${f.expireLe}T23:59:00` : undefined } }), onSuccess: () => { setF({ titre: '', message: '', cibleType: 'tous', cibleId: '', expireLe: '' }); qc.invalidateQueries({ queryKey: ['rhann'] }); } });
  const off = useMutation({ mutationFn: (id: string) => api(`rh/annonces/${id}`, { method: 'PUT', json: { active: false } }), onSuccess: () => qc.invalidateQueries({ queryKey: ['rhann'] }) });
  const targets = f.cibleType === 'delegation' ? deleg.data?.map((d) => ({ id: d.id, l: d.nomDelegFr ?? d.code })) : structs.data?.map((s) => ({ id: s.id, l: `${s.code} · ${s.nomFr}` }));
  return (
    <>
      <Card title={t('rhpub.newAnn')}>
        <Field label={t('rhpub.annTitle')}><input value={f.titre} onChange={(e) => setF({ ...f, titre: e.target.value })} /></Field>
        <Field label={t('rhpub.message')}><textarea value={f.message} onChange={(e) => setF({ ...f, message: e.target.value })} /></Field>
        <div className="ff c2">
          <Field label={t('rhpub.target')}><select value={f.cibleType} onChange={(e) => setF({ ...f, cibleType: e.target.value, cibleId: '' })}>{['tous', 'delegation', 'structure'].map((k) => <option key={k} value={k}>{t(`rhpub.t_${k}`)}</option>)}</select></Field>
          {f.cibleType !== 'tous' ? <Field label={t('rhpub.target')}><select value={f.cibleId} onChange={(e) => setF({ ...f, cibleId: e.target.value })}><option value="" />{targets?.map((x) => <option key={x.id} value={x.id}>{x.l}</option>)}</select></Field> : <span />}
          <Field label={t('rhpub.expires')}><input type="date" value={f.expireLe} onChange={(e) => setF({ ...f, expireLe: e.target.value })} /></Field>
        </div>
        {create.error && <ErrorBox error={create.error} />}
        {create.isSuccess && <p className="okt" role="status">{t('rhpub.published')}</p>}
        <button type="button" className="btn pri" disabled={!f.titre.trim() || !f.message.trim() || create.isPending} onClick={() => create.mutate()}>{t('rhpub.publish')}</button>
      </Card>
      <Card flush>{list.data?.map((a) => <Row key={a.id} main={a.titre} sub={`${fmtDate(locale, a.publieLe)}${a.expireLe ? ` → ${fmtDate(locale, a.expireLe)}` : ''}`} right={a.active ? <button type="button" className="btn sm" onClick={() => off.mutate(a.id)}>{t('rhpub.unpublish')}</button> : <span className="pill n">{t('rhpub.inactive')}</span>} />)}</Card>
    </>
  );
}

function Documents() {
  const t = useTranslations();
  const qc = useQueryClient();
  const [f, setF] = useState({ typeDocId: '', titre: '', accuse: false });
  const [file, setFile] = useState<File | null>(null);
  const types = useQuery({ queryKey: ['rhtypes'], queryFn: () => api<{ id: string; libelle: string }[]>('rh/types-document') });
  const list = useQuery({ queryKey: ['rhdocs'], queryFn: () => api<Doc[]>('rh/documents') });
  const create = useMutation({ mutationFn: () => { const fd = new FormData(); fd.append('typeDocId', f.typeDocId); fd.append('titre', f.titre); fd.append('accuseLecture', String(f.accuse)); if (file) fd.append('fichier', file); return api('rh/documents', { method: 'POST', body: fd }); }, onSuccess: () => { setF({ typeDocId: '', titre: '', accuse: false }); setFile(null); qc.invalidateQueries({ queryKey: ['rhdocs'] }); } });
  const off = useMutation({ mutationFn: (id: string) => api(`rh/documents/${id}`, { method: 'DELETE' }), onSuccess: () => qc.invalidateQueries({ queryKey: ['rhdocs'] }) });
  return (
    <>
      <Card title={t('rhpub.newDoc')}>
        <div className="ff c2">
          <Field label={t('rhpub.docType')}><select value={f.typeDocId} onChange={(e) => setF({ ...f, typeDocId: e.target.value })}><option value="" />{types.data?.map((x) => <option key={x.id} value={x.id}>{x.libelle}</option>)}</select></Field>
          <Field label={t('rhpub.annTitle')}><input value={f.titre} onChange={(e) => setF({ ...f, titre: e.target.value })} /></Field>
        </div>
        <Field label={t('rhpub.pdf')}><input type="file" accept="application/pdf" onChange={(e) => setFile(e.target.files?.[0] ?? null)} /></Field>
        <label><input type="checkbox" checked={f.accuse} onChange={(e) => setF({ ...f, accuse: e.target.checked })} /> {t('rhpub.ack')}</label>
        {create.error && <ErrorBox error={create.error} />}
        {create.isSuccess && <p className="okt" role="status">{t('rhpub.published')}</p>}
        <button type="button" className="btn pri" disabled={!f.typeDocId || !f.titre.trim() || create.isPending} onClick={() => create.mutate()}>{t('rhpub.publish')}</button>
      </Card>
      <Card flush>{list.data?.map((d) => <Row key={d.id} main={d.titre} sub={d.type} right={d.publie ? <button type="button" className="btn sm" onClick={() => off.mutate(d.id)}>{t('rhpub.unpublish')}</button> : <span className="pill n">{t('rhpub.inactive')}</span>} />)}</Card>
    </>
  );
}

export default function Publications() {
  const t = useTranslations();
  const [tab, setTab] = useState<'claims' | 'ann' | 'docs'>('claims');
  return (
    <WebOnly>
      <Page title={t('rhpub.title')}>
        <div className="tabs" role="tablist">{([['claims', 'claims'], ['ann', 'announcements'], ['docs', 'documents']] as const).map(([k, m]) => <button key={k} type="button" role="tab" aria-pressed={tab === k} onClick={() => setTab(k)}>{t(`rhpub.${m}`)}</button>)}</div>
        {tab === 'claims' && <Claims />}{tab === 'ann' && <Announcements />}{tab === 'docs' && <Documents />}
      </Page>
    </WebOnly>
  );
}

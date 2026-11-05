'use client';

import { Suspense, useEffect, useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api, upload } from '@/lib/client';
import { Icon } from '@/components/Icon';
import { Card, ErrorBox, errorMessage, Field, Loading, Page } from '@/components/ui';

type Famille = 'conge' | 'att' | 'form' | 'mis';
const FAMILLES: Famille[] = ['conge', 'att', 'form', 'mis'];
/** Types dont le brouillon se modifie ici (les missions ont leur propre formulaire). */
const FAMILLE_DE: Record<string, Famille> = { CONGE_ANNUEL: 'conge', CONGE_MALADIE: 'conge', CONGE_EXCEPTION: 'conge', ATTESTATION: 'att', FORMATION: 'form' };
interface Draft { typeCode: string; typeLibelle: string; objet?: string; description?: string; dateDebut?: string; dateFin?: string; donnees?: Record<string, unknown>; statut: string; piecesJointes?: { id: string; nomFichier: string }[] }
interface Ref { id: string; nomFr?: string; nomAr?: string; libelleFr?: string; libelleAr?: string }

function Form() {
  const t = useTranslations();
  const locale = useLocale();
  const router = useRouter();
  const qc = useQueryClient();
  const params = useSearchParams();
  const editId = params.get('id');
  const draft = useQuery({ queryKey: ['demande-edit', editId], queryFn: () => api<Draft>(`demandes/${editId}`), enabled: !!editId });
  const famille: Famille = (editId ? FAMILLE_DE[draft.data?.typeCode ?? ''] : (params.get('famille') as Famille)) || 'conge';
  // les missions ont leur propre module (calcul des indemnités, circuit, documents)
  if (famille === 'mis' && typeof window !== 'undefined') window.location.replace(`/${locale}/deplacements/nouvelle`);
  const [v, setV] = useState<Record<string, string>>({ leaveType: 'CONGE_ANNUEL', attType: 'travail' });
  const [files, setFiles] = useState<File[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const set = (k: string) => (e: { target: { value: string } }) => setV({ ...v, [k]: e.target.value });
  const deleg = useQuery({ queryKey: ['ref-deleg'], queryFn: () => api<Ref[]>('referentiels/delegations'), enabled: famille === 'mis' });
  const transport = useQuery({ queryKey: ['ref-transport'], queryFn: () => api<Ref[]>('referentiels/moyens-transport'), enabled: famille === 'mis' });
  // brouillon à modifier : le formulaire est prérempli avec ses valeurs
  useEffect(() => {
    const d = draft.data;
    if (!d) return;
    const x = d.donnees ?? {};
    setV({ leaveType: d.typeCode, attType: String(x.typeAttestation ?? 'travail'), d1: d.dateDebut ?? '', d2: d.dateFin ?? '', motive: d.description ?? '', recipient: d.objet && d.objet !== d.typeLibelle ? d.objet : '',
      title: String(x.intitule ?? ''), organism: String(x.organisme ?? ''), cost: x.cout != null ? String(x.cout) : '', place: String(x.lieu ?? '') });
  }, [draft.data]);
  const solde = useQuery({ queryKey: ['solde'], queryFn: () => api<{ disponible: number }>('me/solde-conge'), enabled: famille === 'conge' });
  const apercu = useQuery({ queryKey: ['conge-apercu', v.d1, v.d2], queryFn: () => api<{ jours: number; soldeAvant: number; soldeApres: number; chevauchement: boolean }>(`me/conge-apercu?debut=${v.d1}&fin=${v.d2}`), enabled: famille === 'conge' && !!v.d1 && !!v.d2 && v.d2 >= v.d1 });
  const name = (r: Ref) => (locale === 'ar' ? r.nomAr ?? r.libelleAr : r.nomFr ?? r.libelleFr) ?? r.id;

  function payload() {
    switch (famille) {
      case 'conge': return { typeCode: v.leaveType, dateDebut: v.d1 || null, dateFin: v.d2 || null, description: v.motive ?? '' };
      case 'att': return { typeCode: 'ATTESTATION', objet: v.recipient || undefined, donnees: { typeAttestation: v.attType } };
      case 'form': return { typeCode: 'FORMATION', dateDebut: v.d1 || null, dateFin: v.d2 || null, donnees: { intitule: v.title, organisme: v.organism, cout: v.cost ? Number(v.cost) : undefined, lieu: v.place } };
      case 'mis': return { typeCode: 'ORDRE_MISSION', objet: v.object, description: v.object, dateDebut: v.d1 || null, dateFin: v.d2 || null, donnees: { departDelegationId: v.depart, arrivalDelegationId: v.arrival, moyenTransportId: v.transport } };
    }
  }

  async function save(submit: boolean) {
    setBusy(true);
    setError(null);
    let id: string | null = null;
    try {
      if (editId) { await api(`demandes/${editId}`, { method: 'PUT', json: payload() }); id = editId; qc.invalidateQueries({ queryKey: ['demande', editId] }); qc.invalidateQueries({ queryKey: ['demandes'] }); }
      else id = (await api<{ id: string }>('demandes', { method: 'POST', json: payload() })).id;
      for (const f of files) await upload(`demandes/${id}/pieces-jointes`, f);
      if (submit) await api(`demandes/${id}/soumettre`, { method: 'POST' });
      router.push(`/${locale}/demandes/${id}`);
    } catch (e) {
      // Le brouillon existe déjà : on l'ouvre pour que l'agent corrige et soumette de nouveau.
      if (id && submit) router.push(`/${locale}/demandes/${id}?err=${(e as { error?: { code?: string } }).error?.code ?? 'generic'}`);
      else { setError(e); setBusy(false); }
    }
  }

  return (
    <Page title={editId ? t('dem.editDraft') : t('dem.new')} sub={editId ? t('dem.editDraftSub') : t('dem.chooseFamily')}>
      {editId && draft.isLoading && <Loading />}
      {editId && draft.data && !FAMILLE_DE[draft.data.typeCode] && <p className="err" role="alert">{t('dem.notEditableHere')}</p>}
      {!editId && <div className="chips" role="group">
        {FAMILLES.map((f) => <Link key={f} className="chip" aria-pressed={f === famille} href={`/${locale}/demandes/nouvelle?famille=${f}`}>{t(`dem.fam_${f}`)}</Link>)}
      </div>}
      <Card>
        {famille === 'conge' && (<>
          <div className="ff c2">
            <Field label={t('dem.leaveType')}><select value={v.leaveType} onChange={set('leaveType')}>{['CONGE_ANNUEL', 'CONGE_MALADIE', 'CONGE_EXCEPTION'].map((c) => <option key={c} value={c}>{t(`dem.t_${c}`)}</option>)}</select></Field>
            <Field label={t('dem.motive')}><input value={v.motive ?? ''} onChange={set('motive')} placeholder={t('dem.motiveHint')} /></Field>
            <Field label={t('dem.from')}><input type="date" value={v.d1 ?? ''} onChange={set('d1')} /></Field>
            <Field label={t('dem.to')}><input type="date" value={v.d2 ?? ''} onChange={set('d2')} /></Field>
          </div>
          {apercu.data && (
            <dl className="kv">
              <div><dt>{t('dem.workdaysCounted')}</dt><dd>{apercu.data.jours} {t('dem.workdays').toLowerCase()}</dd></div>
              <div><dt>{t('dem.balanceBeforeAfter')}</dt><dd className="mono">{String(apercu.data.soldeAvant).replace('.', ',')} → {String(apercu.data.soldeApres).replace('.', ',')}</dd></div>
              <div><dt>{t('dem.excluded')}</dt><dd>{t('dem.excludedValue')}</dd></div>
            </dl>
          )}
          {apercu.data && (apercu.data.chevauchement ? <p className="err">{t('dem.overlap')}</p> : <p className="okt">{t('dem.noOverlap')}</p>)}
          <p className="muted">{t('dem.leaveNote')}</p>
        </>)}
        {famille === 'att' && (<>
          <div className="ff c2">
            <Field label={t('dem.attType')}><select value={v.attType} onChange={set('attType')}><option value="travail">{t('dem.att_travail')}</option><option value="salaire">{t('dem.att_salaire')}</option></select></Field>
            <Field label={t('dem.recipient')}><input value={v.recipient ?? ''} onChange={set('recipient')} /></Field>
          </div>
          <p className="muted">{t('dem.attNote')}</p>
        </>)}
        {famille === 'form' && (
          <div className="ff c2">
            <Field label={t('dem.trainingTitle')}><input value={v.title ?? ''} onChange={set('title')} /></Field>
            <Field label={t('dem.organism')}><input value={v.organism ?? ''} onChange={set('organism')} /></Field>
            <Field label={t('dem.from')}><input type="date" value={v.d1 ?? ''} onChange={set('d1')} /></Field>
            <Field label={t('dem.to')}><input type="date" value={v.d2 ?? ''} onChange={set('d2')} /></Field>
            <Field label={t('dem.cost')}><input type="number" min="0" value={v.cost ?? ''} onChange={set('cost')} /></Field>
            <Field label={t('dem.place')}><input value={v.place ?? ''} onChange={set('place')} /></Field>
          </div>
        )}
        {famille === 'mis' && (<>
          <div className="ff c2">
            <Field label={t('dem.depart')}><select value={v.depart ?? ''} onChange={set('depart')}><option value="" />{deleg.data?.map((d) => <option key={d.id} value={d.id}>{name(d)}</option>)}</select></Field>
            <Field label={t('dem.arrival')}><select value={v.arrival ?? ''} onChange={set('arrival')}><option value="" />{deleg.data?.map((d) => <option key={d.id} value={d.id}>{name(d)}</option>)}</select></Field>
            <Field label={t('dem.transport')}><select value={v.transport ?? ''} onChange={set('transport')}><option value="" />{transport.data?.map((d) => <option key={d.id} value={d.id}>{name(d)}</option>)}</select></Field>
            <span />
            <Field label={t('dem.from')}><input type="date" value={v.d1 ?? ''} onChange={set('d1')} /></Field>
            <Field label={t('dem.to')}><input type="date" value={v.d2 ?? ''} onChange={set('d2')} /></Field>
          </div>
          <Field label={t('dem.object')}><textarea value={v.object ?? ''} onChange={set('object')} /></Field>
          <p className="muted">{t('dem.missionNote')}</p>
        </>)}
        {(famille === 'conge' || famille === 'form') && (
          <label className="drop" style={{ cursor: 'pointer' }}>
            <Icon name="up" /><span>{t('dem.attachDrop')}</span>
            {draft.data?.piecesJointes && draft.data.piecesJointes.length > 0 && <small>{t('dem.alreadyAttached', { n: draft.data.piecesJointes.length })}</small>}
            {files.length > 0 && <small>{files.map((f) => f.name).join(', ')}</small>}
            <input type="file" multiple accept="application/pdf,image/jpeg,image/png" onChange={(e) => setFiles(Array.from(e.target.files ?? []))} style={{ display: 'none' }} />
          </label>
        )}
        {error != null && <ErrorBox error={error} />}
      </Card>
      <div className="acts">
        <button type="button" className="btn" disabled={busy || (!!editId && !draft.data)} onClick={() => save(false)}><Icon name="pencil" />{t('dem.saveDraft')}</button>
        <button type="button" className="btn pri" disabled={busy} onClick={() => save(true)}><Icon name="send" />{t('dem.submit')}</button>
      </div>
    </Page>
  );
}

export default function Nouvelle() {
  return <Suspense fallback={<Loading />}><Form /></Suspense>;
}

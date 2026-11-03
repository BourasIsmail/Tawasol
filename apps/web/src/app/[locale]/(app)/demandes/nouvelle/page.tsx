'use client';

import { Suspense, useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { api, upload } from '@/lib/client';
import { Card, ErrorBox, errorMessage, Field, Loading, Page } from '@/components/ui';

type Famille = 'conge' | 'att' | 'avance' | 'form' | 'mis';
const FAMILLES: Famille[] = ['conge', 'att', 'avance', 'form', 'mis'];
interface Ref { id: string; nomFr?: string; nomAr?: string; libelleFr?: string; libelleAr?: string }

function Form() {
  const t = useTranslations();
  const locale = useLocale();
  const router = useRouter();
  const famille = (useSearchParams().get('famille') as Famille) || 'conge';
  // les missions ont leur propre module (calcul des indemnités, circuit, documents)
  if (famille === 'mis' && typeof window !== 'undefined') window.location.replace(`/${locale}/deplacements/nouvelle`);
  const [v, setV] = useState<Record<string, string>>({ leaveType: 'CONGE_ANNUEL', attType: 'travail' });
  const [files, setFiles] = useState<File[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const set = (k: string) => (e: { target: { value: string } }) => setV({ ...v, [k]: e.target.value });
  const deleg = useQuery({ queryKey: ['ref-deleg'], queryFn: () => api<Ref[]>('referentiels/delegations'), enabled: famille === 'mis' });
  const transport = useQuery({ queryKey: ['ref-transport'], queryFn: () => api<Ref[]>('referentiels/moyens-transport'), enabled: famille === 'mis' });
  const solde = useQuery({ queryKey: ['solde'], queryFn: () => api<{ disponible: number }>('me/solde-conge'), enabled: famille === 'conge' });
  const name = (r: Ref) => (locale === 'ar' ? r.nomAr ?? r.libelleAr : r.nomFr ?? r.libelleFr) ?? r.id;

  function payload() {
    switch (famille) {
      case 'conge': return { typeCode: v.leaveType, dateDebut: v.d1 || null, dateFin: v.d2 || null, description: v.motive ?? '' };
      case 'att': return { typeCode: 'ATTESTATION', objet: v.recipient || undefined, donnees: { typeAttestation: v.attType } };
      case 'avance': return { typeCode: 'AVANCE_SALAIRE', description: v.motive, donnees: { montant: Number(v.amount) } };
      case 'form': return { typeCode: 'FORMATION', dateDebut: v.d1 || null, dateFin: v.d2 || null, donnees: { intitule: v.title, organisme: v.organism, cout: v.cost ? Number(v.cost) : undefined, lieu: v.place } };
      case 'mis': return { typeCode: 'ORDRE_MISSION', objet: v.object, description: v.object, dateDebut: v.d1 || null, dateFin: v.d2 || null, donnees: { departDelegationId: v.depart, arrivalDelegationId: v.arrival, moyenTransportId: v.transport } };
    }
  }

  async function save(submit: boolean) {
    setBusy(true);
    setError(null);
    let id: string | null = null;
    try {
      id = (await api<{ id: string }>('demandes', { method: 'POST', json: payload() })).id;
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
    <Page title={t('dem.new')}>
      <div className="chips" role="group">
        {FAMILLES.map((f) => <Link key={f} className="chip" aria-pressed={f === famille} href={`/${locale}/demandes/nouvelle?famille=${f}`}>{t(`dem.fam_${f}`)}</Link>)}
      </div>
      <Card>
        {famille === 'conge' && (<>
          <div className="ff c2">
            <Field label={t('dem.leaveType')}><select value={v.leaveType} onChange={set('leaveType')}>{['CONGE_ANNUEL', 'CONGE_MALADIE', 'CONGE_EXCEPTION'].map((c) => <option key={c} value={c}>{t(`dem.t_${c}`)}</option>)}</select></Field>
            <Field label={t('dem.motive')}><input value={v.motive ?? ''} onChange={set('motive')} /></Field>
            <Field label={t('dem.from')}><input type="date" value={v.d1 ?? ''} onChange={set('d1')} /></Field>
            <Field label={t('dem.to')}><input type="date" value={v.d2 ?? ''} onChange={set('d2')} /></Field>
          </div>
          {solde.data && <p className="muted">{t('dem.balanceInfo', { n: solde.data.disponible })}</p>}
          <p className="muted">{t('dem.workdaysNote')}</p>
          {v.leaveType === 'CONGE_MALADIE' && <p className="muted">{t('dem.sickNote')}</p>}
          {v.leaveType === 'CONGE_EXCEPTION' && <p className="muted">{t('dem.excNote')}</p>}
        </>)}
        {famille === 'att' && (<>
          <div className="ff c2">
            <Field label={t('dem.attType')}><select value={v.attType} onChange={set('attType')}><option value="travail">{t('dem.att_travail')}</option><option value="salaire">{t('dem.att_salaire')}</option></select></Field>
            <Field label={t('dem.recipient')}><input value={v.recipient ?? ''} onChange={set('recipient')} /></Field>
          </div>
          <p className="muted">{t('dem.attNote')}</p>
        </>)}
        {famille === 'avance' && (
          <div className="ff c2">
            <Field label={t('dem.amount')}><input type="number" min="0" value={v.amount ?? ''} onChange={set('amount')} /></Field>
            <Field label={t('dem.motive')}><input value={v.motive ?? ''} onChange={set('motive')} /></Field>
          </div>
        )}
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
          <Field label={t('dem.attach')}>
            <input type="file" multiple accept="application/pdf,image/jpeg,image/png" onChange={(e) => setFiles(Array.from(e.target.files ?? []))} />
          </Field>
        )}
        {error != null && <ErrorBox error={error} />}
        <div className="acts">
          <button type="button" className="btn" disabled={busy} onClick={() => save(false)}>{t('dem.saveDraft')}</button>
          <button type="button" className="btn pri" disabled={busy} onClick={() => save(true)}>{t('dem.submit')}</button>
        </div>
      </Card>
    </Page>
  );
}

export default function Nouvelle() {
  return <Suspense fallback={<Loading />}><Form /></Suspense>;
}

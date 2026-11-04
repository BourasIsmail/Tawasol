'use client';

import { useEffect, useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useRouter } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate } from '@/lib/format';
import { Card, ErrorBox, Field, Loading, Page, Row } from './ui';
import { Icon } from '@/components/Icon';

interface Fiche { matricule: string; nomFr: string; prenomFr: string; nomAr: string; prenomAr: string; gradeId?: string; structureId: string; dateRecrutement: string; email?: string; telephone?: string; cin?: string; sexe?: string; situationFamiliale?: string; dateNaissance?: string; positionAdministrative?: string; echelon?: string; echelle?: number; dateEchelon?: string }
interface Ref { id: string; code: string | number; nomFr?: string; libelleFr?: string }
interface Ev { id: string; type: string; ancienneValeur?: string; nouvelleValeur: string; dateEffet: string; decisionRef?: string }
const EMPTY: Fiche = { matricule: '', nomFr: '', prenomFr: '', nomAr: '', prenomAr: '', structureId: '', dateRecrutement: '' };

export function PersonnelForm({ id, scope = 'rh' }: { id?: string; scope?: 'rh' | 'admin' }) {
  const base = scope === 'admin' ? 'admin' : 'rh';
  const t = useTranslations();
  const locale = useLocale();
  const router = useRouter();
  const qc = useQueryClient();
  const [tab, setTab] = useState<'id' | 'car'>('id');
  const [f, setF] = useState<Record<string, string>>({});
  const fiche = useQuery({ queryKey: ['rhfiche', scope, id], queryFn: () => api<Fiche>(`${base}/personnel/${id}`), enabled: !!id });
  const structs = useQuery({ queryKey: ['rhstructs'], queryFn: () => api<(Ref & { actif: boolean | number })[]>(`${base}/structures`) });
  const grades = useQuery({ queryKey: ['rhgrades'], queryFn: () => api<Ref[]>(`${base}/grades`) });
  const car = useQuery({ queryKey: ['rhcar', id], queryFn: () => api<Ev[]>(`${base}/personnel/${id}/carriere`), enabled: !!id && tab === 'car' });
  useEffect(() => {
    const src = id ? fiche.data : EMPTY;
    if (src) setF(Object.fromEntries(Object.entries(src).map(([k, v]) => [k, v == null ? '' : String(v)])));
  }, [fiche.data, id]);
  const set = (k: string) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });
  const payload = () => ({ ...f, echelle: f.echelle ? Number(f.echelle) : undefined, gradeId: f.gradeId || undefined });
  const save = useMutation({
    mutationFn: () => (id ? api<{ id: string }>(`${base}/personnel/${id}`, { method: 'PUT', json: payload() }) : api<{ id: string }>('rh/personnel', { method: 'POST', json: payload() })),
    onSuccess: (r) => { qc.invalidateQueries({ queryKey: ['rhfiche'] }); qc.invalidateQueries({ queryKey: ['rhcar'] }); if (!id) router.push(`/${locale}/rh/personnel/${r.id}`); },
  });
  const off = useMutation({ mutationFn: () => api(`rh/personnel/${id}`, { method: 'DELETE' }), onSuccess: () => router.push(`/${locale}/rh/personnel`) });
  if (id && fiche.isLoading) return <Loading />;
  if (fiche.error) return <ErrorBox error={fiche.error} onRetry={() => fiche.refetch()} />;
  const inp = (k: string, label: string, type = 'text') => <Field label={label}><input type={type} value={f[k] ?? ''} onChange={set(k)} /></Field>;
  return (
    <Page title={id ? `${f.prenomFr ?? ''} ${f.nomFr ?? ''}` : t('rhstaff.newAgent')} sub={f.matricule ? <span className="mono">{f.matricule}</span> : undefined}>
      {id && <div className="tabs" role="tablist"><button type="button" role="tab" aria-pressed={tab === 'id'} onClick={() => setTab('id')}>{t('rhstaff.identity')}</button><button type="button" role="tab" aria-pressed={tab === 'car'} onClick={() => setTab('car')}>{t('rhstaff.career')}</button></div>}
      {tab === 'id' && (
        <Card>
          <div className="ff c2">
            {inp('matricule', t('rhstaff.matricule'))}<span />
            {inp('nomFr', t('rhstaff.nameFr'))}{inp('prenomFr', t('rhstaff.firstFr'))}
            <Field label={t('rhstaff.nameAr')}><input dir="rtl" value={f.nomAr ?? ''} onChange={set('nomAr')} /></Field>
            <Field label={t('rhstaff.firstAr')}><input dir="rtl" value={f.prenomAr ?? ''} onChange={set('prenomAr')} /></Field>
            <Field label={t('rhstaff.structure')}><select value={f.structureId ?? ''} onChange={set('structureId')}><option value="" />{structs.data?.filter((s) => s.actif).map((s) => <option key={s.id} value={s.id}>{s.code} · {s.nomFr}</option>)}</select></Field>
            <Field label={t('rhstaff.grade')}><select value={f.gradeId ?? ''} onChange={set('gradeId')}><option value="" />{grades.data?.map((g) => <option key={g.id} value={g.id}>{g.code} · {g.libelleFr}</option>)}</select></Field>
            {inp('dateRecrutement', t('rhstaff.hired'), 'date')}{inp('dateNaissance', t('rhstaff.birth'), 'date')}
            {inp('email', t('rhstaff.email'), 'email')}{inp('telephone', t('rhstaff.phone'))}
            {inp('cin', t('rhstaff.cin'))}
            <Field label={t('rhstaff.sex')}><select value={f.sexe ?? ''} onChange={set('sexe')}><option value="" /><option value="M">M</option><option value="F">F</option></select></Field>
            <Field label={t('rhstaff.family')}><select value={f.situationFamiliale ?? ''} onChange={set('situationFamiliale')}><option value="" />{['celibataire', 'marie', 'divorce', 'veuf'].map((v) => <option key={v} value={v}>{v}</option>)}</select></Field>
            <Field label={t('rhstaff.position')}><select value={f.positionAdministrative ?? 'activite'} onChange={set('positionAdministrative')}>{['activite', 'detachement', 'disponibilite', 'mise_a_disposition', 'autre'].map((v) => <option key={v} value={v}>{v}</option>)}</select></Field>
            {inp('echelle', t('rhstaff.scale'), 'number')}{inp('echelon', t('rhstaff.step'))}
            {inp('dateEchelon', t('rhstaff.stepDate'), 'date')}<span />
            {id && <>{inp('decisionRef', t('rhstaff.decision'))}{inp('dateEffet', t('rhstaff.effect'), 'date')}</>}
          </div>
          {save.error && <ErrorBox error={save.error} />}
          {save.isSuccess && <p className="okt" role="status">{t('rhstaff.saved')}</p>}
          <div className="acts">
            <button type="button" className="btn pri" disabled={save.isPending} onClick={() => save.mutate()}>{t('rhstaff.save')}</button>
            {id && scope === 'rh' && <button type="button" className="btn bad" onClick={() => off.mutate()}>{t('rhstaff.deactivate')}</button>}
          </div>
          <p className="rib"><Icon name="lock" />{t('rhstaff.sensitiveNote')}</p>
        </Card>
      )}
      {tab === 'car' && (
        <Card flush>
          {car.isLoading && <Loading />}
          {car.data?.length === 0 && <p className="muted" style={{ padding: 16, margin: 0 }}>{t('rhstaff.noHistory')}</p>}
          {car.data?.map((e) => <Row key={e.id} main={`${e.type} : ${e.ancienneValeur ?? '—'} → ${e.nouvelleValeur}`} sub={`${fmtDate(locale, e.dateEffet)}${e.decisionRef ? ` · ${e.decisionRef}` : ''}`} />)}
        </Card>
      )}
    </Page>
  );
}

'use client';

import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import Link from 'next/link';
import { useMutation, useQuery } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { Icon } from '@/components/Icon';
import { Card, ErrorBox, errorMessage, Field, Page, WebOnly } from '@/components/ui';

interface Champ { key: string; label: string; obligatoire: boolean }
interface Analyse { colonnes: string[]; apercu: string[][]; nbLignes: number; mappingSuggere: Record<string, string>; mappingModele?: { colonnes: Record<string, string> }; champs: Champ[] }
interface Anomalie { ligne: number; champ: string; code: string; message: string; niveau: string }
interface Controle { rapport: { total: number; valides: number; avertissements: number; erreurs: number; anomalies: Anomalie[] } }
interface Modele { id: string; nom: string }
const TYPES = ['personnel', 'salaire', 'prime', 'credit'];

export default function Imports() {
  const t = useTranslations();
  const locale = useLocale();
  const now = new Date();
  const [step, setStep] = useState(1);
  const [type, setType] = useState('salaire');
  const [modeleId, setModeleId] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [fmt, setFmt] = useState({ encodage: 'UTF-8', separateur: ';', separateurDecimal: ',', formatDate: 'dd/MM/yyyy' });
  const [per, setPer] = useState({ annee: String(now.getFullYear()), mois: String(now.getMonth() || 12) });
  const [ana, setAna] = useState<Analyse | null>(null);
  const [map, setMap] = useState<Record<string, string>>({});
  const [ctl, setCtl] = useState<Controle | null>(null);
  const [replace, setReplace] = useState(false);
  const [modelName, setModelName] = useState('');
  const [lot, setLot] = useState<{ id: string; numero: string; statut: string } | null>(null);
  const modeles = useQuery({ queryKey: ['rhmodeles', type], queryFn: () => api<Modele[]>(`rh/imports/modeles?type=${type}`) });

  const form = (withMapping: boolean) => {
    const fd = new FormData();
    fd.append('type', type); fd.append('fichier', file!);
    if (modeleId) fd.append('modeleId', modeleId);
    fd.append('encodage', fmt.encodage); fd.append('separateur', fmt.separateur); fd.append('separateurDecimal', fmt.separateurDecimal); fd.append('formatDate', fmt.formatDate);
    if (type === 'salaire') { fd.append('annee', per.annee); fd.append('mois', per.mois); }
    if (withMapping) fd.append('mapping', JSON.stringify({ colonnes: Object.fromEntries(Object.entries(map).filter(([, v]) => v)) }));
    return fd;
  };
  const analyse = useMutation({ mutationFn: () => api<Analyse>('rh/imports/analyse', { method: 'POST', body: form(false) }), onSuccess: (a) => { setAna(a); setMap({ ...a.mappingSuggere, ...(a.mappingModele?.colonnes ?? {}) }); setStep(2); } });
  const controle = useMutation({ mutationFn: () => api<Controle>('rh/imports/controle', { method: 'POST', body: form(true) }), onSuccess: (c) => { setCtl(c); setStep(3); } });
  const importer = useMutation({ mutationFn: () => { const fd = form(true); if (replace) fd.append('remplacerBrouillon', 'true'); return api<{ id: string; numero: string; statut: string }>('rh/imports', { method: 'POST', body: fd }); }, onSuccess: (l) => { setLot(l); setStep(4); } });
  const saveModel = useMutation({ mutationFn: () => api('rh/imports/modeles', { method: 'POST', json: { type, nom: modelName, encodage: fmt.encodage, separateur: fmt.separateur, separateurDecimal: fmt.separateurDecimal, formatDate: fmt.formatDate, mapping: { colonnes: Object.fromEntries(Object.entries(map).filter(([, v]) => v)) } } }) });
  const err = analyse.error ?? controle.error ?? importer.error;
  const missing = ana?.champs.filter((c) => c.obligatoire && !map[c.key]) ?? [];
  const hasDraft = ctl?.rapport.anomalies.some((a) => a.code === 'PERIOD_HAS_DRAFT');
  const steps = ['s1', 's2', 's3', 's4'];
  const reset = () => { setStep(1); setAna(null); setCtl(null); setLot(null); setFile(null); setReplace(false); analyse.reset(); controle.reset(); importer.reset(); };

  return (
    <WebOnly>
      <Page title={t('rhimp.title')}>
        <ol className="steps">{steps.map((s, i) => <li key={s} className={i + 1 === step ? 'on' : i + 1 < step ? 'done' : ''}><Icon name={i + 1 < step ? 'check' : 'clock'} />{t(`rhimp.${s}`)}</li>)}</ol>
        {step === 1 && (
          <Card>
            <div className="ff c2">
              <Field label={t('rhimp.type')}><select value={type} onChange={(e) => { setType(e.target.value); setModeleId(''); }}>{TYPES.map((x) => <option key={x} value={x}>{t(`rhimp.t_${x}`)}</option>)}</select></Field>
              <Field label={t('rhimp.model')}><select value={modeleId} onChange={(e) => setModeleId(e.target.value)}><option value="">{t('rhimp.noModel')}</option>{modeles.data?.map((m) => <option key={m.id} value={m.id}>{m.nom}</option>)}</select></Field>
              {type === 'salaire' && <><Field label={t('rhimp.year')}><input type="number" value={per.annee} onChange={(e) => setPer({ ...per, annee: e.target.value })} /></Field><Field label={t('rhimp.month')}><input type="number" min="1" max="12" value={per.mois} onChange={(e) => setPer({ ...per, mois: e.target.value })} /></Field></>}
            </div>
            <details><summary>{t('rhimp.format')}</summary>
              <div className="ff c2" style={{ marginTop: 10 }}>
                <Field label={t('rhimp.encoding')}><select value={fmt.encodage} onChange={(e) => setFmt({ ...fmt, encodage: e.target.value })}><option>UTF-8</option><option>windows-1256</option><option>windows-1252</option></select></Field>
                <Field label={t('rhimp.separator')}><select value={fmt.separateur} onChange={(e) => setFmt({ ...fmt, separateur: e.target.value })}><option value=";">;</option><option value=",">,</option><option value="\t">tab</option></select></Field>
                <Field label={t('rhimp.decimal')}><select value={fmt.separateurDecimal} onChange={(e) => setFmt({ ...fmt, separateurDecimal: e.target.value })}><option value=",">,</option><option value=".">.</option></select></Field>
                <Field label={t('rhimp.dateFormat')}><input value={fmt.formatDate} onChange={(e) => setFmt({ ...fmt, formatDate: e.target.value })} /></Field>
              </div>
            </details>
            <div className="drop"><Icon name="up" /><Field label={t('rhimp.file')}><input type="file" accept=".xlsx,.csv" onChange={(e) => setFile(e.target.files?.[0] ?? null)} /></Field></div>
            {err != null && <ErrorBox error={err} />}
            <button type="button" className="btn pri" disabled={!file || analyse.isPending} onClick={() => analyse.mutate()}>{t('rhimp.analyse')}</button>
          </Card>
        )}
        {step === 2 && ana && (
          <Card>
            <p className="muted" style={{ margin: 0 }}>{file?.name} · {ana.nbLignes}</p>
            <div className="scrollx"><table className="tbl"><thead><tr><th>{t('rhimp.field')}</th><th>{t('rhimp.columnFile')}</th></tr></thead>
              <tbody>{ana.champs.map((c) => (
                <tr key={c.key}><td>{c.label}{c.obligatoire && <span className="pill warn" style={{ marginInlineStart: 6 }}>{t('rhimp.required')}</span>}</td>
                  <td><select aria-label={c.label} value={map[c.key] ?? ''} onChange={(e) => setMap({ ...map, [c.key]: e.target.value })}><option value="">{t('rhimp.ignore')}</option>{ana.colonnes.filter(Boolean).map((x) => <option key={x} value={x}>{x}</option>)}</select></td></tr>
              ))}</tbody></table></div>
            <div className="ban"><div><small>{t('rhimp.modelHint')}</small></div></div>
            <div className="acts"><input aria-label={t('rhimp.modelName')} placeholder={t('rhimp.modelName')} value={modelName} onChange={(e) => setModelName(e.target.value)} style={{ border: '1px solid var(--line)', borderRadius: 8, padding: '8px 10px', background: 'var(--surface)', color: 'inherit' }} /><button type="button" className="btn sm" disabled={!modelName.trim() || saveModel.isPending} onClick={() => saveModel.mutate()}>{t('rhimp.saveModel')}</button>{saveModel.isSuccess && <span className="okt">{t('rhimp.modelSaved')}</span>}</div>
            {err != null && <ErrorBox error={err} />}
            <div className="acts"><button type="button" className="btn" onClick={() => setStep(1)}>{t('rhimp.prev')}</button><button type="button" className="btn pri" disabled={missing.length > 0 || controle.isPending} onClick={() => controle.mutate()}>{t('rhimp.check')}</button></div>
          </Card>
        )}
        {step === 3 && ctl && (
          <>
            <div className="g4"><div className="stat"><span>{t('rhimp.valid')}</span><b>{ctl.rapport.valides}</b></div><div className="stat"><span>{t('rhimp.warnings')}</span><b>{ctl.rapport.avertissements}</b></div><div className="stat"><span>{t('rhimp.errors')}</span><b>{ctl.rapport.erreurs}</b></div></div>
            {ctl.rapport.anomalies.length > 0 && (
              <Card flush>{ctl.rapport.anomalies.slice(0, 100).map((a, i) => <div className="rw" key={i}><div className="rm"><b>{a.ligne ? `${t('rhimp.line')} ${a.ligne}` : '—'}</b><small>{a.message}</small></div><div className="rr"><span className={`pill ${a.niveau === 'ERREUR' ? 'bad' : 'warn'}`}>{a.niveau === 'ERREUR' ? '✕' : '⚠'} {a.code}</span></div></div>)}</Card>
            )}
            {hasDraft && <label className="fld"><span><input type="checkbox" checked={replace} onChange={(e) => setReplace(e.target.checked)} /> {t('rhimp.replaceDraft')}</span></label>}
            {err != null && <ErrorBox error={err} />}
            <div className="acts"><button type="button" className="btn" onClick={() => setStep(2)}>{t('rhimp.prev')}</button><button type="button" className="btn pri" disabled={importer.isPending || ctl.rapport.valides === 0 || (hasDraft && !replace)} onClick={() => importer.mutate()}>{t('rhimp.importNow', { n: ctl.rapport.valides })}</button></div>
          </>
        )}
        {step === 4 && lot && (
          <Card>
            <div className="ban ok" role="status"><div><b>{lot.statut === 'brouillon' ? t('rhimp.importedDraft', { numero: lot.numero }) : t('rhimp.importedDirect', { numero: lot.numero })}</b></div></div>
            <div className="acts"><Link className="btn pri" href={`/${locale}/rh/lots`}>{t('rhimp.goLots')}</Link><a className="btn" href={`/api/bff/rh/imports/lots/${lot.id}/rapport`} target="_blank" rel="noopener">{t('rhimp.report')}</a><button type="button" className="btn" onClick={reset}>{t('rhimp.newImport')}</button></div>
          </Card>
        )}
      </Page>
    </WebOnly>
  );
}

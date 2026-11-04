'use client';

import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { Icon } from '@/components/Icon';
import { SoldesParAnnee } from '@/components/SoldesParAnnee';
import { CongesTabs } from '@/components/CongesTabs';
import { Card, ErrorBox, Field, Loading, Page, WebOnly } from '@/components/ui';

interface Neg { personnelId: string; matricule: string; nomFr: string; prenomFr: string; nomAr: string; prenomAr: string; annee: number; droit: number; reporte: number; pris: number; disponible: number; source: string; aVerifier: boolean | number }
interface Ctl { parAnnee: { annee: number; joursImputes: number; joursPris: number; ecart: number }[]; coherent: boolean; negatifsTotal: number; anneeNegatifs: number; negatifs: Neg[]; parSource: { source: string; n: number; aVerifier: number }[]; aRegulariser: number }
interface Fiche { id: string; matricule: string; nomFr: string; prenomFr: string; nomAr: string; prenomAr: string; categorieAgent?: string; solde: { annee: number; droit: number; pris: number; reporte: number; reserve: number; disponible: number; source: string; aVerifier: boolean } }

/** Cohérence des soldes (approuvés = pris), soldes négatifs à revoir, correction agent par agent. */
export default function ControleSoldes() {
  const t = useTranslations();
  const locale = useLocale();
  const qc = useQueryClient();
  const [annee, setAnnee] = useState(new Date().getFullYear());
  const [mat, setMat] = useState('');
  const [f, setF] = useState<Fiche | null>(null);
  const [form, setForm] = useState({ droit: '', reporte: '', pris: '', categorie: '' });
  const [saved, setSaved] = useState(false);
  const q = useQuery({ queryKey: ['cg-check', annee], queryFn: () => api<Ctl>(`rh/conges/controle?annee=${annee}`) });
  const open = useMutation({
    mutationFn: (m: string) => api<Fiche>(`rh/conges/solde?matricule=${encodeURIComponent(m.trim())}&annee=${annee}`),
    onSuccess: (r) => { setF(r); setSaved(false); setForm({ droit: String(r.solde.droit), reporte: String(r.solde.reporte), pris: String(r.solde.pris), categorie: r.categorieAgent ?? '' }); },
  });
  const save = useMutation({
    mutationFn: () => api(`rh/conges/soldes/${f!.id}/${annee}`, { method: 'PUT', json: { droit: Number(form.droit), reporte: Number(form.reporte), pris: Number(form.pris), categorieAgent: form.categorie } }),
    onSuccess: () => { setSaved(true); qc.invalidateQueries({ queryKey: ['cg-check'] }); open.mutate(f!.matricule); },
  });
  const nm = (x: { nomFr: string; prenomFr: string; nomAr: string; prenomAr: string }) => (locale === 'ar' ? `${x.prenomAr} ${x.nomAr}` : `${x.prenomFr} ${x.nomFr}`);
  const d = q.data;
  return (
    <WebOnly>
      <Page title={t('cg.title')} sub={t('cg.sub')}>
        <CongesTabs />
        <div className="ban warn"><Icon name="alert" /><div><small>{t('cg.paramsToValidate')}</small></div></div>
        <Field label={t('cg.year')}><input type="number" min={2000} max={2100} value={annee} onChange={(e) => setAnnee(Number(e.target.value))} style={{ maxWidth: 120 }} /></Field>
        {q.isLoading && <Loading />}
        {q.error && <ErrorBox error={q.error} onRetry={() => q.refetch()} />}
        {d && (
          <>
            <div className="g4">
              <div className="stat"><span>{t('cg.consistency')}</span><b style={{ color: d.coherent ? 'var(--ok)' : 'var(--bad)' }}>{d.coherent ? t('cg.ok') : t('cg.gap')}</b></div>
              <div className="stat"><span>{t('cg.negatives', { y: d.anneeNegatifs })}</span><b style={{ color: d.negatifsTotal ? 'var(--warn)' : undefined }}>{d.negatifsTotal}</b></div>
              <div className="stat"><span>{t('cg.toRegularize')}</span><b style={{ color: d.aRegulariser ? 'var(--warn)' : undefined }}>{d.aRegulariser}</b></div>
              <div className="stat"><span>{t('cg.toVerify')}</span><b>{d.parSource.reduce((a, s) => a + Number(s.aVerifier ?? 0), 0)}</b></div>
            </div>
            <Card title={t('cg.byYear')} flush>
              <table className="tbl"><thead><tr><th>{t('cg.year')}</th><th>{t('cg.imputedDays')}</th><th>{t('cg.takenDays')}</th><th>{t('cg.diff')}</th></tr></thead>
                <tbody>{d.parAnnee.map((a) => <tr key={a.annee}><td>{a.annee}</td><td className="mono">{a.joursImputes}</td><td className="mono">{a.joursPris}</td><td className="mono" style={{ color: Number(a.ecart) === 0 ? undefined : 'var(--bad)' }}>{a.ecart}</td></tr>)}</tbody></table>
            </Card>
            <Card title={t('cg.negTitle', { y: d.anneeNegatifs })} flush>
              {d.negatifs.length === 0 ? <p className="muted" style={{ padding: 16, margin: 0 }}>{t('cg.noNeg')}</p> : (
                <div className="scrollx"><table className="tbl"><thead><tr><th>{t('rhstaff.matricule')}</th><th>{t('rhreq.colAgent')}</th><th>{t('cg.right')}</th><th>{t('cg.carry')}</th><th>{t('cg.taken2')}</th><th>{t('cg.left')}</th><th>{t('cg.source')}</th><th /></tr></thead>
                  <tbody>{d.negatifs.map((n) => (
                    <tr key={n.personnelId}><td className="mono">{n.matricule}</td><td>{nm(n)}</td><td className="mono">{n.droit}</td><td className="mono">{n.reporte}</td><td className="mono">{n.pris}</td><td className="mono" style={{ color: 'var(--bad)' }}>{n.disponible}</td>
                      <td><span className="pill">{t(`cg.src_${n.source}`)}</span>{n.aVerifier ? <> <span className="pill warn">{t('cg.verify')}</span></> : null}</td>
                      <td><button type="button" className="btn sm" onClick={() => { setMat(n.matricule); open.mutate(n.matricule); }}>{t('cg.fix')}</button></td></tr>
                  ))}</tbody></table></div>
              )}
            </Card>
          </>
        )}
        <Card title={t('cg.fixTitle')}>
          <div className="acts" style={{ alignItems: 'end' }}>
            <Field label={t('rhstaff.matricule')}><input value={mat} onChange={(e) => setMat(e.target.value)} /></Field>
            <button type="button" className="btn" disabled={!mat.trim() || open.isPending} onClick={() => open.mutate(mat)}>{t('cg.load')}</button>
          </div>
          {open.error != null && <ErrorBox error={open.error} />}
          {f && (
            <>
              <p><b>{nm(f)}</b> · <span className="mono">{f.matricule}</span> · {t('cg.source')} : <span className="pill">{t(`cg.src_${f.solde.source}`)}</span> {f.solde.aVerifier && <span className="pill warn">{t('cg.verify')}</span>} · {t('cg.available')} : <b>{f.solde.disponible}</b> ({t('cg.reserved')} {f.solde.reserve})</p>
              <SoldesParAnnee path={`rh/conges/soldes/${f.id}`} />
              <div className="ff c2">
                <Field label={t('cg.right')}><input type="number" min="0" max="365" step="0.5" value={form.droit} onChange={(e) => setForm({ ...form, droit: e.target.value })} /></Field>
                <Field label={t('cg.carry')}><input type="number" min="0" max="365" step="0.5" value={form.reporte} onChange={(e) => setForm({ ...form, reporte: e.target.value })} /></Field>
                <Field label={t('cg.taken2')}><input type="number" min="0" max="365" step="0.5" value={form.pris} onChange={(e) => setForm({ ...form, pris: e.target.value })} /></Field>
                <Field label={t('cg.category')} hint={t('cg.categoryHint')}><input value={form.categorie} maxLength={30} onChange={(e) => setForm({ ...form, categorie: e.target.value })} /></Field>
              </div>
              {save.error != null && <ErrorBox error={save.error} />}
              {saved && <div className="ban ok" role="status"><Icon name="check" /><div><b>{t('cg.saved')}</b></div></div>}
              <div className="acts"><button type="button" className="btn pri" disabled={save.isPending} onClick={() => save.mutate()}><Icon name="check" />{t('cg.save')}</button></div>
            </>
          )}
        </Card>
      </Page>
    </WebOnly>
  );
}

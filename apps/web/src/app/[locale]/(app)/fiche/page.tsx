'use client';

import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import Link from 'next/link';
import { useMutation } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate } from '@/lib/format';
import { Icon } from '@/components/Icon';
import { Card, ErrorBox, Field, Loading, Page } from '@/components/ui';
import { useMe } from '@/components/AppShell';

interface Fiche { matricule: string; nomFr: string; prenomFr: string; nomAr: string; prenomAr: string; gradeFr?: string; gradeAr?: string; structureFr: string; structureAr: string; echelon?: string; echelle?: number; dateEchelon?: string; dateEchelle?: string; dateRecrutement: string; email?: string }

export default function FichePage() {
  const t = useTranslations();
  const locale = useLocale();
  const me = useMe();
  const [mode, setMode] = useState<'idle' | 'ok' | 'err' | 'sent'>('idle');
  const [form, setForm] = useState({ champ: 'grade', valeurProposee: '', description: '' });
  const confirm = useMutation({ mutationFn: () => api('me/fiche/confirmer', { method: 'POST' }), onSuccess: () => setMode('ok') });
  const report = useMutation({ mutationFn: () => api('me/fiche/signaler-erreur', { method: 'POST', json: form }), onSuccess: () => setMode('sent') });
  if (me.isLoading) return <Loading />;
  const f = me.data?.personnel as unknown as Fiche;
  const ar = locale === 'ar';
  const rows: [string, string][] = [
    [t('fiche.matricule'), f.matricule], [t('fiche.nameFr'), `${f.nomFr} ${f.prenomFr}`], [t('fiche.nameAr'), `${f.nomAr} ${f.prenomAr}`],
    [t('fiche.grade'), (ar ? f.gradeAr : f.gradeFr) ?? '—'], [t('fiche.scale'), f.echelle != null ? String(f.echelle) : '—'], [t('fiche.step'), f.echelon ?? '—'], [t('fiche.scaleDate'), fmtDate(locale, f.dateEchelle)], [t('fiche.stepDate'), fmtDate(locale, f.dateEchelon)],
    [t('fiche.hired'), f.dateRecrutement ? fmtDate(locale, f.dateRecrutement) : t('fiche.notProvided')], [t('fiche.structure'), ar ? f.structureAr : f.structureFr], [t('fiche.email'), f.email ?? '—'],
  ];
  return (
    <Page title={t('fiche.title')} sub={t('fiche.intro')}>
      <Card>
        <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 16 }}>
          <div className="av" style={{ width: 56, height: 56, fontSize: 18 }} aria-hidden="true">{`${f.prenomFr[0] ?? ''}${f.nomFr[0] ?? ''}`}</div>
          <div><b style={{ fontSize: 17 }}>{ar ? `${f.prenomAr} ${f.nomAr}` : `${f.prenomFr} ${f.nomFr}`}</b><div className="muted mono" style={{ fontSize: 12 }}>{t('fiche.matricule')} {f.matricule}</div></div>
        </div>
        <dl className="kv">{rows.filter(([k]) => k !== t('fiche.matricule')).map(([k, v]) => <div key={k}><dt>{k}</dt><dd className={k === t('fiche.stepDate') || k === t('fiche.scaleDate') || (k === t('fiche.hired') && !!f.dateRecrutement) || k === t('fiche.email') ? 'mono' : undefined}>{v}</dd></div>)}</dl>
      </Card>
      {mode === 'idle' && (
        <div className="acts">
          <button type="button" className="btn pri" disabled={confirm.isPending} onClick={() => confirm.mutate()}><Icon name="check" />{t('fiche.allCorrect')}</button>
          <button type="button" className="btn" onClick={() => setMode('err')}><Icon name="alert" />{t('fiche.reportError')}</button>
        </div>
      )}
      {confirm.error && <ErrorBox error={confirm.error} />}
      {mode === 'ok' && <div className="ban ok" role="status"><div><b>{t('fiche.confirmed')}</b></div></div>}
      {mode === 'sent' && <div className="ban ok" role="status"><div><b>{t('fiche.reportSent')}</b></div><Link className="btn sm" href={`/${locale}/reclamations`}>{t('common.view')}</Link></div>}
      {mode === 'err' && (
        <Card title={t('fiche.reportError')}>
          <div className="ff c2">
            <Field label={t('fiche.field')}>
              <select value={form.champ} onChange={(e) => setForm({ ...form, champ: e.target.value })}>
                {['grade', 'echelon', 'structure', 'name', 'other'].map((k) => <option key={k} value={k}>{t(`fiche.f_${k}`)}</option>)}
              </select>
            </Field>
            <Field label={t('fiche.proposed')}><input value={form.valeurProposee} onChange={(e) => setForm({ ...form, valeurProposee: e.target.value })} /></Field>
          </div>
          <Field label={t('fiche.explanation')}><textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></Field>
          {report.error && <ErrorBox error={report.error} />}
          <div className="acts">
            <button type="button" className="btn pri" disabled={report.isPending || !form.description.trim()} onClick={() => report.mutate()}>{t('common.send')}</button>
            <button type="button" className="btn" onClick={() => setMode('idle')}>{t('common.cancel')}</button>
          </div>
        </Card>
      )}
    </Page>
  );
}

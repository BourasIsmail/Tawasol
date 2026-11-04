'use client';

import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { Icon } from '@/components/Icon';
import { Card, ErrorBox, Field } from '@/components/ui';

interface Role { id: string; code: string; libelleFr: string; libelleAr: string; profil: string }
interface Candidat { id: string; matricule: string; nomFr: string; prenomFr: string; nomAr: string; prenomAr: string; structureFr?: string; structureAr?: string; aEmail: boolean | number }
interface Resultat { id: string; login: string; lienEnvoye: boolean; motDePasseInitial?: string | null }

/** Création de compte : l'agent est choisi dans la liste des agents sans compte ; le mot de passe est généré, jamais saisi. */
export function CreateAccount({ scope, onDone }: { scope: 'admin' | 'rh'; onDone?: () => void }) {
  const t = useTranslations();
  const locale = useLocale();
  const qc = useQueryClient();
  const base = scope === 'admin' ? 'admin/utilisateurs' : 'rh/comptes';
  const [q, setQ] = useState('');
  const [pid, setPid] = useState('');
  const [roleId, setRoleId] = useState('');
  const [login, setLogin] = useState('');
  const roles = useQuery({ queryKey: [scope, 'roles-creation'], queryFn: () => api<Role[]>(scope === 'admin' ? 'admin/roles' : 'rh/comptes/roles') });
  const cands = useQuery({ queryKey: [scope, 'candidats', q], queryFn: () => api<Candidat[]>(`${base}/candidats?q=${encodeURIComponent(q)}`) });
  const create = useMutation({
    mutationFn: () => api<Resultat>(base, { method: 'POST', json: { personnelId: pid || undefined, roleId, login: login || undefined } }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-comptes'] }); qc.invalidateQueries({ queryKey: [scope, 'candidats'] }); },
  });
  const nm = (c: Candidat) => (locale === 'ar' ? `${c.prenomAr} ${c.nomAr}` : `${c.prenomFr} ${c.nomFr}`);
  const r = create.data;
  if (r) {
    return (
      <Card title={t('acc.created')}>
        <div className="ban ok" role="status"><Icon name="check" /><div><b>{t('acc.createdFor', { login: r.login })}</b><small>{r.lienEnvoye ? t('acc.linkSent') : t('acc.showOnce')}</small></div></div>
        {r.motDePasseInitial && <p><code className="mono" style={{ fontSize: 16 }}>{r.motDePasseInitial}</code></p>}
        <p className="muted" style={{ margin: 0 }}>{t('acc.mustChange')}</p>
        <div className="acts"><button type="button" className="btn" onClick={() => { create.reset(); setPid(''); setLogin(''); setQ(''); onDone?.(); }}>{t('acc.another')}</button></div>
      </Card>
    );
  }
  return (
    <Card title={t('acc.newTitle')}>
      <Field label={t('acc.agent')}>
        <input value={q} placeholder={t('acc.search')} onChange={(e) => { setQ(e.target.value); setPid(''); }} />
      </Field>
      <div style={{ maxHeight: 220, overflowY: 'auto', border: '1px solid var(--line)', borderRadius: 8 }}>
        {cands.data?.length === 0 && <p className="muted">{t('acc.noCandidate')}</p>}
        {cands.data?.map((c) => (
          <button key={c.id} type="button" className="rw" aria-pressed={pid === c.id} onClick={() => setPid(c.id)} style={{ width: '100%', textAlign: 'start', background: pid === c.id ? 'var(--brand-soft)' : 'none', border: 0, cursor: 'pointer', font: 'inherit', color: 'inherit' }}>
            <div className="rm"><b>{nm(c)}</b><small>{c.matricule} · {(locale === 'ar' ? c.structureAr : c.structureFr) ?? ''}</small></div>
            <div className="rr">{!c.aEmail || c.aEmail === 0 ? <span className="pill warn">{t('acc.noEmail')}</span> : null}</div>
          </button>
        ))}
      </div>
      <div className="ff c2">
        <Field label={t('acc.role')}>
          <select value={roleId} onChange={(e) => setRoleId(e.target.value)}>
            <option value="" />
            {roles.data?.map((x) => <option key={x.id} value={x.id}>{locale === 'ar' ? x.libelleAr : x.libelleFr}</option>)}
          </select>
        </Field>
        <Field label={t('acc.login')} hint={t('acc.loginHint')}><input value={login} onChange={(e) => setLogin(e.target.value)} /></Field>
      </div>
      {create.error != null && <ErrorBox error={create.error} />}
      <div className="acts"><button type="button" className="btn pri" disabled={!pid || !roleId || create.isPending} onClick={() => create.mutate()}><Icon name="plus" />{t('acc.create')}</button></div>
      <p className="muted" style={{ margin: 0 }}>{t('acc.rule')}</p>
    </Card>
  );
}

'use client';

import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDateTime } from '@/lib/format';
import { Card, Empty, ErrorBox, Field, Loading, Page } from '@/components/ui';

interface Compte { id: string; login: string; profil: string; roleId?: string; roleCode?: string; estActif: boolean; legacy: boolean; doitChangerMdp: boolean; derniereCnx?: string; matricule?: string; nomFr?: string; prenomFr?: string }
interface Role { id: string; code: string; libelleFr: string; libelleAr: string; profil: string; permissions: string[] }

export default function Comptes() {
  const t = useTranslations();
  const locale = useLocale();
  const qc = useQueryClient();
  const [q, setQ] = useState('');
  const [filtre, setFiltre] = useState<'' | 'legacy' | 'actif' | 'inactif'>('');
  const [page, setPage] = useState(0);
  const params = `q=${encodeURIComponent(q)}&page=${page}${filtre === 'legacy' ? '&legacy=true' : filtre === 'actif' ? '&actif=true' : filtre === 'inactif' ? '&actif=false' : ''}`;
  const comptes = useQuery({ queryKey: ['admin-comptes', params], queryFn: () => api<{ content: Compte[]; total: number; size: number }>(`admin/utilisateurs?${params}`) });
  const roles = useQuery({ queryKey: ['admin-roles'], queryFn: () => api<Role[]>('admin/roles') });
  const save = useMutation({
    mutationFn: (v: { id: string; roleId?: string; estActif?: boolean }) => api(`admin/utilisateurs/${v.id}`, { method: 'PUT', json: { roleId: v.roleId, estActif: v.estActif } }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-comptes'] }),
  });
  const toggle = (c: Compte) => {
    const msg = c.estActif ? t('adm.confirmDisable') : c.legacy ? t('adm.confirmLegacy') : null;
    if (msg && !window.confirm(msg)) return;
    save.mutate({ id: c.id, estActif: !c.estActif });
  };
  const lib = (r: Role) => (locale === 'ar' ? r.libelleAr : r.libelleFr);
  const total = comptes.data?.total ?? 0, size = comptes.data?.size ?? 25;
  return (
    <Page title={t('adm.title')} sub={t('adm.intro')}>
      <div className="chips" role="group">
        {(['', 'actif', 'inactif', 'legacy'] as const).map((f) => (
          <button key={f} type="button" className="chip" aria-pressed={filtre === f} onClick={() => { setFiltre(f); setPage(0); }}>{f === '' ? t('adm.all') : f === 'actif' ? t('adm.active') : f === 'inactif' ? t('adm.inactive') : t('adm.legacy')}</button>
        ))}
      </div>
      <Field label={t('adm.search')}><input value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} /></Field>
      {comptes.isLoading && <Loading />}
      {comptes.error && <ErrorBox error={comptes.error} onRetry={() => comptes.refetch()} />}
      {save.error != null && <ErrorBox error={save.error} />}
      {comptes.data && (comptes.data.content.length === 0 ? <Card><Empty title={t('mis.empty')} /></Card> : (
        <Card flush>
          <table className="tbl"><thead><tr><th>{t('adm.login')}</th><th>{t('adm.agent')}</th><th>{t('adm.role')}</th><th>{t('adm.status')}</th><th>{t('adm.lastLogin')}</th><th /></tr></thead>
            <tbody>{comptes.data.content.map((c) => (
              <tr key={c.id}>
                <td className="mono">{c.login}{c.legacy && <small> · {t('adm.legacyTag')}</small>}</td>
                <td>{c.matricule ? `${c.matricule} · ${c.nomFr ?? ''} ${c.prenomFr ?? ''}` : '—'}</td>
                <td>
                  <select aria-label={t('adm.role')} value={c.roleId ?? ''} onChange={(e) => e.target.value && save.mutate({ id: c.id, roleId: e.target.value })}>
                    <option value="">{t('adm.noRole')}</option>
                    {roles.data?.map((r) => <option key={r.id} value={r.id}>{lib(r)}</option>)}
                  </select>
                </td>
                <td><span className={`pill ${c.estActif ? 'ok' : 'n'}`}><span aria-hidden="true">{c.estActif ? '✓' : '⊘'}</span>{c.estActif ? t('adm.enabled') : t('adm.disabled')}</span>{c.doitChangerMdp && <small> · {t('adm.mustChange')}</small>}</td>
                <td className="mono">{c.derniereCnx ? fmtDateTime(locale, c.derniereCnx) : '—'}</td>
                <td><button type="button" className={`btn sm ${c.estActif ? 'bad' : ''}`} disabled={save.isPending} onClick={() => toggle(c)}>{c.estActif ? t('adm.disable') : t('adm.enable')}</button></td>
              </tr>
            ))}</tbody></table>
        </Card>
      ))}
      <div className="acts">
        <button type="button" className="btn" disabled={page === 0} onClick={() => setPage(page - 1)}>{t('adm.prev')}</button>
        <button type="button" className="btn" disabled={(page + 1) * size >= total} onClick={() => setPage(page + 1)}>{t('adm.next')}</button>
      </div>
      {roles.data && (
        <Card title={t('adm.permissions')}>
          {roles.data.map((r) => <dl className="kv" key={r.id}><div><dt>{lib(r)} <small className="mono">({r.profil})</small></dt><dd className="mono">{r.permissions.join(' · ') || '—'}</dd></div></dl>)}
        </Card>
      )}
    </Page>
  );
}

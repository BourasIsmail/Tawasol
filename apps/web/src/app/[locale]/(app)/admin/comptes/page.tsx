'use client';

import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { Icon } from '@/components/Icon';
import { Card, Empty, ErrorBox, Loading, Page } from '@/components/ui';

interface Compte { id: string; login: string; profil: string; roleId?: string; estActif: boolean; legacy: boolean; doitChangerMdp: boolean; matricule?: string; nomFr?: string; prenomFr?: string }
interface Role { id: string; code: string; libelleFr: string; libelleAr: string }

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
  const reset = useMutation({ mutationFn: (id: string) => api(`admin/utilisateurs/${id}/reinitialiser`, { method: 'POST' }) });
  const toggle = (c: Compte) => {
    const msg = c.estActif ? t('adm.confirmDisable') : c.legacy ? t('adm.confirmLegacy') : null;
    if (msg && !window.confirm(msg)) return;
    save.mutate({ id: c.id, estActif: !c.estActif });
  };
  const lib = (r: Role) => (locale === 'ar' ? r.libelleAr : r.libelleFr);
  const total = comptes.data?.total ?? 0, size = comptes.data?.size ?? 25;
  return (
    <Page title={t('adm.accTitle')}>
      <div className="chips" role="group">
        {(['', 'actif', 'inactif', 'legacy'] as const).map((f) => (
          <button key={f} type="button" className="chip" aria-pressed={filtre === f} onClick={() => { setFiltre(f); setPage(0); }}>{f === '' ? t('adm.all') : f === 'actif' ? t('adm.active') : f === 'inactif' ? t('adm.inactive') : t('adm.legacy')}</button>
        ))}
        <input className="in" style={{ maxWidth: 260 }} placeholder={t('adm.search')} aria-label={t('adm.search')} value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
      </div>
      {comptes.isLoading && <Loading />}
      {comptes.error && <ErrorBox error={comptes.error} onRetry={() => comptes.refetch()} />}
      {(save.error != null || reset.error != null) && <ErrorBox error={save.error ?? reset.error} />}
      {reset.isSuccess && <div className="ban ok" role="status"><Icon name="check" /><div><b>{t('adm.resetDone')}</b></div></div>}
      {comptes.data && (comptes.data.content.length === 0 ? <Card><Empty title={t('mis.empty')} /></Card> : (
        <Card flush>
          <div className="scroll">
            <table className="tbl"><thead><tr><th>{t('adm.colLogin')}</th><th>{t('adm.colRole')}</th><th>{t('adm.colAgent')}</th><th>{t('adm.colState')}</th><th /></tr></thead>
              <tbody>{comptes.data.content.map((c) => (
                <tr key={c.id}>
                  <td className="mono">{c.login}</td>
                  <td>
                    <select aria-label={t('adm.colRole')} value={c.roleId ?? ''} onChange={(e) => e.target.value && save.mutate({ id: c.id, roleId: e.target.value })} style={{ border: 0, background: 'none', padding: 0, minHeight: 0 }}>
                      <option value="">{c.profil}</option>
                      {roles.data?.map((r) => <option key={r.id} value={r.id}>{lib(r)}</option>)}
                    </select>
                  </td>
                  <td>{c.matricule ? `${(c.prenomFr ?? '')[0] ?? ''}. ${c.nomFr ?? ''}` : '—'}</td>
                  <td>
                    {!c.estActif ? <span className="pill"><Icon name="ban" />{t('adm.disabled')}</span>
                      : c.doitChangerMdp ? <span className="pill warn"><Icon name="lock" />{t('adm.mdpChange')}</span>
                      : <span className="pill ok"><Icon name="check" />{t('adm.enabled')}</span>}
                    {c.legacy && <small className="muted"> · {t('adm.legacyTag')}</small>}
                  </td>
                  <td><span className="acts" style={{ justifyContent: 'flex-end' }}>
                    <button type="button" className="btn sm" disabled={reset.isPending} onClick={() => window.confirm(t('adm.confirmReset')) && reset.mutate(c.id)}>{t('adm.reset')}</button>
                    <button type="button" className="btn sm" disabled={save.isPending} onClick={() => toggle(c)}>{c.estActif ? t('adm.disable') : t('adm.enable')}</button>
                  </span></td>
                </tr>
              ))}</tbody></table>
          </div>
        </Card>
      ))}
      <div className="acts">
        <button type="button" className="btn sm" disabled={page === 0} onClick={() => setPage(page - 1)}>{t('adm.prev')}</button>
        <button type="button" className="btn sm" disabled={(page + 1) * size >= total} onClick={() => setPage(page + 1)}>{t('adm.next')}</button>
      </div>
    </Page>
  );
}

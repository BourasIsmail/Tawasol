'use client';

import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate } from '@/lib/format';
import { Icon } from '@/components/Icon';
import { Card, ErrorBox, Field, Loading, Page, Row } from '@/components/ui';

interface Perim { id: string; type: 'structure' | 'delegation' | 'coordination'; dateDebut: string; matricule: string; nomFr: string; prenomFr: string; nomAr: string; prenomAr: string; cibleFr: string; cibleAr: string }
interface Cible { id: string; nomFr: string; nomAr: string }
interface Role { id: string; code: string; libelleFr: string; libelleAr: string; profil: string; permissions: string[] }

export default function Perimetres() {
  const t = useTranslations();
  const locale = useLocale();
  const qc = useQueryClient();
  const [type, setType] = useState<'structure' | 'delegation' | 'coordination'>('structure');
  const [matricule, setMatricule] = useState('');
  const [cible, setCible] = useState('');
  const [debut, setDebut] = useState(new Date().toISOString().slice(0, 10));
  const l = useQuery({ queryKey: ['admin-perim'], queryFn: () => api<Perim[]>('admin/perimetres') });
  const cibles = useQuery({ queryKey: ['admin-cibles', type], queryFn: () => api<Cible[]>(`admin/cibles?type=${type}`) });
  const roles = useQuery({ queryKey: ['admin-roles'], queryFn: () => api<Role[]>('admin/roles') });
  const close = useMutation({ mutationFn: (id: string) => api(`admin/perimetres/${id}/cloturer`, { method: 'POST' }), onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-perim'] }) });
  const add = useMutation({
    mutationFn: () => api('admin/perimetres', { method: 'POST', json: { personnelId: matricule.trim(), type, cibleId: cible, dateDebut: debut } }),
    onSuccess: () => { setMatricule(''); qc.invalidateQueries({ queryKey: ['admin-perim'] }); },
  });
  const nm = (p: Perim) => (locale === 'ar' ? `${p.prenomAr} ${p.nomAr}` : `${p.prenomFr} ${p.nomFr}`);
  const cib = (c: Cible) => (locale === 'ar' ? c.nomAr : c.nomFr);
  const where = (p: Perim) => t(`adm.${p.type}Of`, { name: locale === 'ar' ? p.cibleAr : p.cibleFr });
  const lib = (r: Role) => (locale === 'ar' ? r.libelleAr : r.libelleFr);
  return (
    <Page title={t('adm.permTitle')}>
      {l.isLoading && <Loading />}
      {l.error && <ErrorBox error={l.error} onRetry={() => l.refetch()} />}
      {l.data && (
        <Card flush>
          {l.data.map((p) => (
            <Row key={p.id} main={nm(p)} sub={where(p)}
              right={<><span className="pill ok">{t('adm.since', { date: fmtDate(locale, p.dateDebut) })}</span><button type="button" className="btn sm" disabled={close.isPending} onClick={() => close.mutate(p.id)}>{t('adm.close')}</button></>} />
          ))}
        </Card>
      )}
      <small className="muted">{t('adm.permNote')}</small>
      <Card title={t('adm.newDesig')}>
        <div className="ff c2">
          <Field label={t('adm.matricule')}><input value={matricule} onChange={(e) => setMatricule(e.target.value)} /></Field>
          <Field label={t('adm.scopeType')}><select value={type} onChange={(e) => { setType(e.target.value as typeof type); setCible(''); }}><option value="structure">{t('adm.t_structure')}</option><option value="delegation">{t('adm.t_delegation')}</option><option value="coordination">{t('adm.t_coordination')}</option></select></Field>
          <Field label={t('adm.scopeTarget')}><select value={cible} onChange={(e) => setCible(e.target.value)}><option value="" />{cibles.data?.map((c) => <option key={c.id} value={c.id}>{cib(c)}</option>)}</select></Field>
          <Field label={t('adm.startDate')}><input type="date" value={debut} onChange={(e) => setDebut(e.target.value)} /></Field>
        </div>
        {add.error != null && <ErrorBox error={add.error} />}
        {add.isSuccess && <div className="ban ok" role="status"><Icon name="check" /><div><b>{t('adm.designated')}</b></div></div>}
        <div className="acts"><button type="button" className="btn pri" disabled={!matricule.trim() || !cible || add.isPending} onClick={() => add.mutate()}><Icon name="plus" />{t('adm.designate')}</button></div>
      </Card>
      {roles.data && (
        <Card title={t('adm.permissions')}>
          {roles.data.map((r) => <dl className="kv" key={r.id}><div><dt>{lib(r)} <small className="mono">({r.profil})</small></dt><dd className="mono">{r.permissions.join(' · ') || '—'}</dd></div></dl>)}
        </Card>
      )}
    </Page>
  );
}

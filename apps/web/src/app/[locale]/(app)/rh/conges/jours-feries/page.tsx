'use client';

import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate } from '@/lib/format';
import { Icon } from '@/components/Icon';
import { CongesTabs } from '@/components/CongesTabs';
import { Card, ErrorBox, Field, Loading, Page, WebOnly } from '@/components/ui';

interface JF { id: string; date: string; libelleFr: string; libelleAr: string; type: string; aConfirmer: number | boolean }

/** Jours fériés tenus par la RH : ils sont exclus du décompte des jours ouvrables. Les dates religieuses variables sont à confirmer. */
export default function JoursFeries() {
  const t = useTranslations();
  const locale = useLocale();
  const qc = useQueryClient();
  const [annee, setAnnee] = useState(new Date().getFullYear());
  const [n, setN] = useState({ date: '', libelleFr: '', libelleAr: '', type: 'civil', aConfirmer: false });
  const q = useQuery({ queryKey: ['cg-feries', annee], queryFn: () => api<JF[]>(`rh/jours-feries?annee=${annee}`) });
  const refresh = () => qc.invalidateQueries({ queryKey: ['cg-feries'] });
  const add = useMutation({ mutationFn: () => api('rh/jours-feries', { method: 'POST', json: n }), onSuccess: () => { setN({ date: '', libelleFr: '', libelleAr: '', type: 'civil', aConfirmer: false }); refresh(); } });
  const upd = useMutation({ mutationFn: (j: JF) => api(`rh/jours-feries/${j.id}`, { method: 'PUT', json: { date: j.date, libelleFr: j.libelleFr, libelleAr: j.libelleAr, type: j.type, aConfirmer: !j.aConfirmer } }), onSuccess: refresh });
  const del = useMutation({ mutationFn: (id: string) => api(`rh/jours-feries/${id}`, { method: 'DELETE' }), onSuccess: refresh });
  return (
    <WebOnly>
      <Page title={t('cg.title')} sub={t('cg.sub')}>
        <CongesTabs />
        <div className="ban"><Icon name="shield" /><div><small>{t('cg.holidaysRule')}</small></div></div>
        <Field label={t('cg.year')}><input type="number" min={2000} max={2100} value={annee} onChange={(e) => setAnnee(Number(e.target.value))} style={{ maxWidth: 120 }} /></Field>
        {q.isLoading && <Loading />}
        {q.error && <ErrorBox error={q.error} onRetry={() => q.refetch()} />}
        {q.data && (
          <Card flush>
            <div className="scrollx"><table className="tbl"><thead><tr><th>{t('cg.date')}</th><th>{t('cg.labelFr')}</th><th>{t('cg.labelAr')}</th><th>{t('cg.type')}</th><th>{t('cg.toConfirm')}</th><th /></tr></thead>
              <tbody>{q.data.map((j) => (
                <tr key={j.id}><td className="mono">{fmtDate(locale, j.date)}</td><td>{j.libelleFr}</td><td dir="rtl">{j.libelleAr}</td><td>{t(`cg.t_${j.type}`)}</td>
                  <td><button type="button" className={`btn sm${j.aConfirmer ? '' : ''}`} onClick={() => upd.mutate(j)}>{j.aConfirmer ? t('cg.confirmDate') : t('cg.confirmed')}</button></td>
                  <td><button type="button" className="btn sm bad" onClick={() => { if (window.confirm(t('cg.confirmDelete'))) del.mutate(j.id); }}><Icon name="x" />{t('cg.delete')}</button></td></tr>
              ))}</tbody></table></div>
          </Card>
        )}
        <Card title={t('cg.addHoliday')}>
          <div className="ff c2">
            <Field label={t('cg.date')}><input type="date" value={n.date} onChange={(e) => setN({ ...n, date: e.target.value })} /></Field>
            <Field label={t('cg.type')}><select value={n.type} onChange={(e) => setN({ ...n, type: e.target.value })}><option value="civil">{t('cg.t_civil')}</option><option value="religieux">{t('cg.t_religieux')}</option></select></Field>
            <Field label={t('cg.labelFr')}><input value={n.libelleFr} onChange={(e) => setN({ ...n, libelleFr: e.target.value })} /></Field>
            <Field label={t('cg.labelAr')}><input dir="rtl" value={n.libelleAr} onChange={(e) => setN({ ...n, libelleAr: e.target.value })} /></Field>
          </div>
          <label><input type="checkbox" checked={n.aConfirmer} onChange={(e) => setN({ ...n, aConfirmer: e.target.checked })} /> {t('cg.toConfirmHint')}</label>
          {add.error != null && <ErrorBox error={add.error} />}
          <div className="acts"><button type="button" className="btn pri" disabled={!n.date || !n.libelleFr.trim() || !n.libelleAr.trim() || add.isPending} onClick={() => add.mutate()}><Icon name="plus" />{t('cg.add')}</button></div>
        </Card>
      </Page>
    </WebOnly>
  );
}

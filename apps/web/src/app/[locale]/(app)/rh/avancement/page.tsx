'use client';

import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate } from '@/lib/format';
import { Card, Empty, ErrorBox, Field, Loading, Page } from '@/components/ui';

interface Prop { id: string; matricule: string; nomFr: string; prenomFr: string; grade: string; nbEchelons: number; echelon: string; dateEchelon: string; dateEligibilite: string }

export default function Avancement() {
  const t = useTranslations();
  const locale = useLocale();
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['avancement'], queryFn: () => api<{ dureeMois: number; parametreValide: boolean; propositions: Prop[] }>('rh/avancement/propositions') });
  const [sel, setSel] = useState<string | null>(null);
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const [ref, setRef] = useState('');
  const go = useMutation({
    mutationFn: (id: string) => api(`rh/avancement/${id}/echelon`, { method: 'POST', json: { dateEffet: date, decisionRef: ref } }),
    onSuccess: () => { setSel(null); setRef(''); qc.invalidateQueries({ queryKey: ['avancement'] }); },
  });
  return (
    <Page title={t('adv.title')} sub={t('adv.intro')}>
      {q.isLoading && <Loading />}
      {q.error && <ErrorBox error={q.error} onRetry={() => q.refetch()} />}
      {q.data && !q.data.parametreValide && <div className="ban warn"><div><small>{t('adv.unvalidated', { n: q.data.dureeMois })}</small></div></div>}
      {go.error != null && <ErrorBox error={go.error} />}
      {q.data && (q.data.propositions.length === 0 ? <Card><Empty title={t('adv.empty')} /></Card> : (
        <Card flush>
          <table className="tbl"><thead><tr><th>{t('fiche.matricule')}</th><th /><th>{t('fiche.grade')}</th><th>{t('adv.step')}</th><th>{t('adv.eligible')}</th><th /></tr></thead>
            <tbody>{q.data.propositions.map((p) => (
              <tr key={p.id}><td className="mono">{p.matricule}</td><td>{p.nomFr} {p.prenomFr}</td><td>{p.grade}</td><td>{p.echelon} → {Number(p.echelon) + 1} / {p.nbEchelons}</td><td className="mono">{fmtDate(locale, p.dateEligibilite)}</td>
                <td>{sel === p.id ? (
                  <span className="acts">
                    <input type="date" aria-label={t('adv.effectDate')} value={date} onChange={(e) => setDate(e.target.value)} />
                    <input aria-label={t('adv.decisionRef')} placeholder={t('adv.decisionRef')} value={ref} onChange={(e) => setRef(e.target.value)} />
                    <button type="button" className="btn pri sm" disabled={!ref.trim() || go.isPending} onClick={() => go.mutate(p.id)}>{t('mis.confirm')}</button>
                  </span>
                ) : <button type="button" className="btn sm" onClick={() => setSel(p.id)}>{t('adv.validate')}</button>}</td></tr>
            ))}</tbody></table>
        </Card>
      ))}
    </Page>
  );
}

'use client';

import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate } from '@/lib/format';
import { Icon } from '@/components/Icon';
import { CongesTabs } from '@/components/CongesTabs';
import { Card, Empty, ErrorBox, Field, Loading, Page, WebOnly } from '@/components/ui';

interface Row { id: string; numero?: string; typeLibelle: string; statut: string; dateDebut: string; dateFin: string; nbJours: number; matricule: string; nomFr: string; prenomFr: string; nomAr: string; prenomAr: string; herite: number | boolean }

/** Congés « soumis » dont la période est passée : la RH tranche (pris ou jamais pris), y compris en lot, jamais automatiquement. */
export default function Regulariser() {
  const t = useTranslations();
  const locale = useLocale();
  const qc = useQueryClient();
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [sel, setSel] = useState<Set<string>>(new Set());
  const [comment, setComment] = useState('');
  const [missing, setMissing] = useState(false);
  const [done, setDone] = useState<{ traitees: number; ignorees: number } | null>(null);
  const list = useQuery({ queryKey: ['cg-reg', q, page], queryFn: () => api<{ content: Row[]; total: number; size: number }>(`rh/conges/a-regulariser?q=${encodeURIComponent(q)}&page=${page}&size=50`) });
  const run = useMutation({
    mutationFn: (decision: 'approuver' | 'rejeter') => api<{ traitees: number; ignorees: number }>('rh/conges/regulariser', { method: 'POST', json: { ids: [...sel], decision, commentaire: comment } }),
    onSuccess: (r) => { setDone(r); setSel(new Set()); setComment(''); qc.invalidateQueries({ queryKey: ['cg-reg'] }); qc.invalidateQueries({ queryKey: ['cg-check'] }); qc.invalidateQueries({ queryKey: ['rhdash'] }); },
  });
  const rows = list.data?.content ?? [];
  const nm = (x: Row) => (locale === 'ar' ? `${x.prenomAr} ${x.nomAr}` : `${x.prenomFr} ${x.nomFr}`);
  const toggle = (id: string) => setSel((s) => { const n = new Set(s); if (n.has(id)) n.delete(id); else n.add(id); return n; });
  const all = rows.length > 0 && rows.every((r) => sel.has(r.id));
  function go(decision: 'approuver' | 'rejeter') {
    if (!comment.trim()) { setMissing(true); return; }
    setMissing(false);
    if (!window.confirm(t(decision === 'approuver' ? 'cg.confirmTaken' : 'cg.confirmNever', { n: sel.size }))) return;
    run.mutate(decision);
  }
  return (
    <WebOnly>
      <Page title={t('cg.title')} sub={t('cg.sub')}>
        <CongesTabs />
        <div className="ban"><Icon name="shield" /><div><small>{t('cg.rule')}</small></div></div>
        {done && <div className="ban ok" role="status"><Icon name="check" /><div><b>{t('cg.done', { n: done.traitees })}</b>{done.ignorees > 0 && <small>{t('cg.skipped', { n: done.ignorees })}</small>}</div></div>}
        <input aria-label={t('rhstaff.search')} placeholder={t('rhstaff.search')} value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} style={{ maxWidth: 320, border: '1px solid var(--line)', borderRadius: 8, padding: '9px 11px', background: 'var(--surface)', color: 'inherit' }} />
        {list.isLoading && <Loading />}
        {list.error && <ErrorBox error={list.error} onRetry={() => list.refetch()} />}
        {list.data && (rows.length === 0 ? <Card><Empty title={t('cg.empty')} /></Card> : (
          <Card flush title={t('cg.count', { n: list.data.total })}>
            <div className="scrollx"><table className="tbl">
              <thead><tr><th><input type="checkbox" aria-label={t('cg.selectAll')} checked={all} onChange={() => setSel(all ? new Set() : new Set(rows.map((r) => r.id)))} /></th><th>{t('rhstaff.matricule')}</th><th>{t('rhreq.colAgent')}</th><th>{t('rhreq.colType')}</th><th>{t('dem.period')}</th><th>{t('cg.days')}</th></tr></thead>
              <tbody>{rows.map((r) => (
                <tr key={r.id}>
                  <td><input type="checkbox" aria-label={nm(r)} checked={sel.has(r.id)} onChange={() => toggle(r.id)} /></td>
                  <td className="mono">{r.matricule}</td><td>{nm(r)}</td><td>{r.typeLibelle}{r.herite ? <> <span className="pill">{t('cg.legacy')}</span></> : null}</td>
                  <td className="mono">{fmtDate(locale, r.dateDebut)} → {fmtDate(locale, r.dateFin)}</td><td>{r.nbJours}</td>
                </tr>
              ))}</tbody></table></div>
          </Card>
        ))}
        <div className="acts"><button type="button" className="btn sm" disabled={page === 0} onClick={() => setPage(page - 1)}>{t('rhimp.prev')}</button><button type="button" className="btn sm" disabled={!list.data || (page + 1) * list.data.size >= list.data.total} onClick={() => setPage(page + 1)}>{t('rhimp.next')}</button></div>
        {sel.size > 0 && (
          <Card title={t('cg.selected', { n: sel.size })}>
            <Field label={t('cg.comment')}><textarea value={comment} onChange={(e) => setComment(e.target.value)} aria-invalid={missing} /></Field>
            {missing && <p className="err" role="alert">{t('rval.commentRequired')}</p>}
            {run.error != null && <ErrorBox error={run.error} />}
            <div className="acts">
              <button type="button" className="btn ok" disabled={run.isPending} onClick={() => go('approuver')}><Icon name="check" />{t('cg.taken')}</button>
              <button type="button" className="btn bad" disabled={run.isPending} onClick={() => go('rejeter')}><Icon name="x" />{t('cg.never')}</button>
            </div>
          </Card>
        )}
      </Page>
    </WebOnly>
  );
}

'use client';

import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDateTime } from '@/lib/format';
import { Card, Empty, ErrorBox, Loading, Page, Pill, Row, WebOnly } from '@/components/ui';

interface Lot { id: string; numero: string; type: string; statut: string; periodeAnnee?: number; periodeMois?: number; fichierNom: string; nbLignes: number; nbErreurs: number; creeLe: string }

export default function Lots() {
  const t = useTranslations();
  const locale = useLocale();
  const qc = useQueryClient();
  const [confirm, setConfirm] = useState<string | null>(null);
  const q = useQuery({ queryKey: ['rhlots'], queryFn: () => api<Lot[]>('rh/imports/lots') });
  const done = () => { setConfirm(null); qc.invalidateQueries({ queryKey: ['rhlots'] }); qc.invalidateQueries({ queryKey: ['rhdash'] }); };
  const pub = useMutation({ mutationFn: (id: string) => api(`rh/imports/lots/${id}/publier`, { method: 'POST' }), onSuccess: done });
  const cancel = useMutation({ mutationFn: (id: string) => api(`rh/imports/lots/${id}/annuler`, { method: 'POST' }), onSuccess: done });
  return (
    <WebOnly>
      <Page title={t('rhlots.title')} sub={t('rhlots.intro')}>
        {q.isLoading && <Loading />}
        {q.error && <ErrorBox error={q.error} onRetry={() => q.refetch()} />}
        {(pub.error || cancel.error) && <ErrorBox error={pub.error ?? cancel.error} />}
        {confirm && <div className="ban warn" role="alertdialog"><div><b>{t('rhlots.confirmPublish')}</b></div><button type="button" className="btn pri" disabled={pub.isPending} onClick={() => pub.mutate(confirm)}>{t('rhlots.publish')}</button><button type="button" className="btn" onClick={() => setConfirm(null)}>{t('common.cancel')}</button></div>}
        {q.data && (q.data.length === 0 ? <Card><Empty title={t('rhlots.empty')} /></Card> : (
          <Card flush>{q.data.map((l) => (
            <Row key={l.id} main={<><span className="mono">{l.numero}</span> · {t(`rhimp.t_${l.type}`)}{l.periodeMois ? ` · ${l.periodeMois}/${l.periodeAnnee}` : ''}</>}
              sub={`${l.fichierNom} · ${t('rhlots.lines', { n: l.nbLignes })} · ${t('rhlots.errorsN', { n: l.nbErreurs })} · ${fmtDateTime(locale, l.creeLe)}`}
              right={<>
                <Pill status={l.statut === 'publie' ? 'approuvee' : l.statut === 'brouillon' ? 'brouillon' : 'annulee'} label={t(`rhlots.${l.statut}`)} />
                {l.nbErreurs > 0 && <a className="btn sm" href={`/api/bff/rh/imports/lots/${l.id}/rapport`} target="_blank" rel="noopener">{t('rhimp.report')}</a>}
                {l.statut === 'brouillon' && <><button type="button" className="btn sm pri" onClick={() => setConfirm(l.id)}>{t('rhlots.publish')}</button><button type="button" className="btn sm bad" onClick={() => cancel.mutate(l.id)}>{t('rhlots.cancel')}</button></>}
              </>} />
          ))}</Card>
        ))}
      </Page>
    </WebOnly>
  );
}

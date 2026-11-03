'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate, fmtMoney } from '@/lib/format';
import { Icon } from '@/components/Icon';
import { Card, Empty, ErrorBox, Loading, Page, Pill, Row } from '@/components/ui';
import { MISSION_STATUTS, useDroits, type Mission } from '@/lib/missions';

export default function Deplacements() {
  const t = useTranslations();
  const locale = useLocale();
  const initial = useSearchParams().get('statut') ?? '';
  const [statut, setStatut] = useState(initial);
  useEffect(() => setStatut(initial), [initial]);
  const droits = useDroits();
  const q = useQuery({ queryKey: ['missions', statut], queryFn: () => api<{ content: Mission[] }>(`missions?size=50${statut ? `&statut=${statut}` : ''}`) });
  const d = droits.data;
  const pure = !!d && !d.analytics && !d.valider && !d.validerNiveau1 && !d.validerPaiement && !d.saisirPerimetre && !d.etats;
  return (
    <Page title={pure ? t('mis.mine') : t('mis.title')} sub={pure ? undefined : t('mis.sub')} actions={<>
      {droits.data?.analytics && <Link className="btn" href={`/${locale}/deplacements/analytique`}>{t('mis.analytics')}</Link>}
      {(droits.data?.demander || droits.data?.saisirPerimetre) && <Link className="btn pri" href={`/${locale}/deplacements/nouvelle`}><Icon name="plus" />{t('mis.new')}</Link>}
    </>}>
      {!pure && <div className="chips" role="group">
        <button type="button" className="chip" aria-pressed={statut === ''} onClick={() => setStatut('')}>{t('mis.all')}</button>
        {MISSION_STATUTS.map((s) => <button type="button" key={s} className="chip" aria-pressed={statut === s} onClick={() => setStatut(s)}>{t(`status.${s}`)}</button>)}
      </div>}
      {q.isLoading && <Loading />}
      {q.error && <ErrorBox error={q.error} onRetry={() => q.refetch()} />}
      {q.data && (q.data.content.length === 0
        ? <Card><Empty title={t('mis.empty')} hint={t('mis.emptyHint')} /></Card>
        : <Card flush>{q.data.content.map((m) => (
            <Row key={m.id} href={`/${locale}/deplacements/${m.id}`} main={pure ? `${(m.arrivalDelegation ?? '').replace(/^Délégation d[eu']?\s*/i, '')} · ${m.objet}` : `${m.agentNom} · ${m.arrivalDelegation ?? ''}`}
              sub={`${m.numeroOrdm ?? m.numeroOrdmLegacy ?? '—'} · ${fmtDate(locale, m.dateDepart)}${m.dateRetour ? ` → ${fmtDate(locale, m.dateRetour)}` : ''}${pure ? '' : ` · ${m.objet}`}`}
              right={<><span className="mono">{m.montantTotal > 0 ? `${fmtMoney(locale, m.montantTotal)} ${t('common.mad')}` : ''}</span><Pill status={m.statut} label={t(`status.${m.statut}`)} /></>} />
          ))}</Card>)}
    </Page>
  );
}

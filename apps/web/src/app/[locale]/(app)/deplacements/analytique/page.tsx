'use client';

import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtMoney } from '@/lib/format';
import { Card, ErrorBox, Field, Loading, Page } from '@/components/ui';

interface Ligne { [k: string]: string | number }
interface Analytique { parStatut: Ligne[]; parMois: Ligne[]; parDelegation: Ligne[]; parProgramme: Ligne[]; anomalies: Record<string, number | null> }

function Tableau({ titre, lignes, cle, libelle }: { titre: string; lignes: Ligne[]; cle: string; libelle?: (v: string | number) => string }) {
  const t = useTranslations();
  const locale = useLocale();
  return (
    <Card title={titre}>
      <table className="tbl"><thead><tr><th /><th>{t('mis.count')}</th><th>{t('mis.amount')}</th></tr></thead>
        <tbody>{lignes.map((l, i) => <tr key={i}><td>{libelle ? libelle(l[cle]) : l[cle]}</td><td>{l.nombre}</td><td className="mono">{fmtMoney(locale, Number(l.montant))}</td></tr>)}</tbody></table>
    </Card>
  );
}

export default function AnalytiqueMissions() {
  const t = useTranslations();
  const [annee, setAnnee] = useState(new Date().getFullYear());
  const q = useQuery({ queryKey: ['mission-analytique', annee], queryFn: () => api<Analytique>(`missions/analytique?annee=${annee}`) });
  return (
    <Page title={t('mis.analytics')} actions={<>
      <a className="btn" href={`/api/bff/missions/export?annee=${annee}`} download>{t('mis.exportList')}</a>
      <a className="btn" href={`/api/bff/missions/etat-global?annee=${annee}`} download>{t('mis.exportGlobal')}</a>
    </>}>
      <Field label={t('mis.year')}><input type="number" value={annee} min={2020} max={2100} onChange={(e) => setAnnee(Number(e.target.value))} /></Field>
      {q.isLoading && <Loading />}
      {q.error && <ErrorBox error={q.error} onRetry={() => q.refetch()} />}
      {q.data && (
        <div className="g2">
          <Tableau titre={t('mis.byStatus')} lignes={q.data.parStatut} cle="statut" libelle={(v) => t(`status.${v}`)} />
          <Tableau titre={t('mis.byMonth')} lignes={q.data.parMois} cle="mois" />
          <Tableau titre={t('mis.byDeleg')} lignes={q.data.parDelegation} cle="delegation" />
          <Tableau titre={t('mis.byProgram')} lignes={q.data.parProgramme} cle="programme" />
          <Card title={t('mis.anomalies')}>
            <dl className="kv">
              <div><dt>{t('mis.w_RETOUR_NON_RENSEIGNE')}</dt><dd>{q.data.anomalies.retourNonRenseigne ?? 0}</dd></div>
              <div><dt>{t('mis.w_DEPART_EGAL_ARRIVEE')}</dt><dd>{q.data.anomalies.departEgalArrivee ?? 0}</dd></div>
              <div><dt>{t('mis.historicAmount')}</dt><dd>{q.data.anomalies.montantHistoriqueNonReproductible ?? 0}</dd></div>
              <div><dt>{t('mis.w_NUMERO_ABSENT')} / {t('mis.w_ETAT_EN_DOUBLE')}</dt><dd>{q.data.anomalies.anomaliesNumero ?? 0}</dd></div>
            </dl>
          </Card>
        </div>
      )}
    </Page>
  );
}

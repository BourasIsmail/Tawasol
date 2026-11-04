'use client';

import { useTranslations } from 'next-intl';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { Card, ErrorBox, Loading } from '@/components/ui';

interface Solde { annee: number; droit: number; reporte: number; pris: number; reserve: number; disponible: number; source: string; aVerifier: boolean }

/**
 * Jours de congé restants année par année : droit, report, pris, réservé (demandes en cours à venir), reste.
 * L'origine du chiffre est dite (saisi RH, officiel, reconstitué, calculé) ; « à vérifier » signale un chiffre reconstitué.
 */
export function SoldesParAnnee({ path, title }: { path: string; title?: string }) {
  const t = useTranslations();
  const q = useQuery({ queryKey: ['soldes-par-annee', path], queryFn: () => api<Solde[]>(path) });
  if (q.isLoading) return <Loading />;
  if (q.error) return <ErrorBox error={q.error} onRetry={() => q.refetch()} />;
  const rows = q.data ?? [];
  return (
    <Card title={title ?? t('cg.byYearTitle')} flush>
      <div className="scrollx"><table className="tbl">
        <thead><tr><th>{t('cg.year')}</th><th>{t('cg.right')}</th><th>{t('cg.carry')}</th><th>{t('cg.taken2')}</th><th>{t('cg.reserved2')}</th><th>{t('cg.left')}</th><th>{t('cg.source')}</th></tr></thead>
        <tbody>{rows.map((s) => (
          <tr key={s.annee}>
            <td><b>{s.annee}</b></td><td className="mono">{s.droit}</td><td className="mono">{s.reporte}</td><td className="mono">{s.pris}</td><td className="mono">{s.reserve}</td>
            <td className="mono" style={{ color: Number(s.disponible) < 0 ? 'var(--bad)' : undefined }}><b>{s.disponible}</b></td>
            <td><span className="pill">{t(`cg.src_${s.source}`)}</span>{s.aVerifier ? <> <span className="pill warn">{t('cg.verify')}</span></> : null}</td>
          </tr>
        ))}</tbody>
      </table></div>
      <p className="muted" style={{ margin: 0, padding: '10px 16px', fontSize: 12 }}>{t('cg.byYearNote')}</p>
    </Card>
  );
}

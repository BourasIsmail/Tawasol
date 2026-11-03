'use client';

import { Icon } from './Icon';
import { useLocale, useTranslations } from 'next-intl';
import { fmtDate } from '@/lib/format';

export interface EtapeChaine {
  ordre: number; niveau: string; statut: string; parSuppleance?: boolean; dateDecision?: string; commentaire?: string;
  nomFr: string; prenomFr: string; nomAr: string; prenomAr: string;
}

const MARK: Record<string, string> = { en_attente: 'clock', validee: 'check', rejetee: 'x', ignoree: 'chev' };

/** Avancement de la validation hiérarchique : un niveau par responsable, dans l'ordre. L'état est dit par le texte et le symbole, jamais par la couleur seule. */
export function Chaine({ etapes }: { etapes?: EtapeChaine[] | null }) {
  const t = useTranslations();
  const locale = useLocale();
  if (!etapes) return null;
  if (etapes.length === 0) return <p className="muted">{t('chain.none')}</p>;
  const actif = etapes.find((e) => e.statut === 'en_attente')?.ordre;
  return (
    <ol className="tl">
      {etapes.map((e) => (
        <li key={e.ordre} aria-current={e.ordre === actif ? 'step' : undefined}>
          <span className="d" aria-hidden="true"><Icon name={MARK[e.statut] ?? 'clock'} /></span>
          <div>
            <b>{t('chain.level', { n: e.ordre, total: etapes.length })} · {t(`chain.n_${e.niveau}`)}</b>
            <small>
              {locale === 'ar' ? `${e.prenomAr} ${e.nomAr}` : `${e.prenomFr} ${e.nomFr}`} · {t(`chain.s_${e.statut}`)}
              {e.dateDecision ? ` · ${fmtDate(locale, e.dateDecision)}` : ''}{e.parSuppleance ? ` · ${t('chain.bySub')}` : ''}
            </small>
            {e.commentaire && <q>{e.commentaire}</q>}
          </div>
        </li>
      ))}
    </ol>
  );
}

import { getTranslations, setRequestLocale } from 'next-intl/server';
import { notFound } from 'next/navigation';

const API = process.env.API_INTERNAL_URL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

interface Resultat { valide: boolean; type: string; numero: string; statut: string; dateEmission?: string }

/** Page publique atteinte par le QR code d'un document : aucune connexion, aucune donnée personnelle. */
export default async function Verification({ params }: { params: Promise<{ locale: string; type: string; id: string; jeton: string }> }) {
  const { locale, type, id, jeton } = await params;
  setRequestLocale(locale);
  const t = await getTranslations();
  let r: Resultat | null = null;
  if (/^[a-z-]+$/.test(type) && /^[0-9a-f-]{36}$/.test(id) && /^[A-Za-z0-9_-]+$/.test(jeton)) {
    const res = await fetch(`${API}/api/v1/verification/${type}/${id}/${jeton}`, { cache: 'no-store' }).catch(() => null);
    if (res?.ok) r = (await res.json()) as Resultat;
  }
  if (!r && !/^[a-z-]+$/.test(type)) notFound();
  return (
    <main style={{ maxWidth: 520, margin: '48px auto', padding: '0 16px' }}>
      <h1>{t('mis.verifyTitle')}</h1>
      {r ? (
        <section className="panel"><div className="cb">
          <p><b>{r.valide ? t('mis.verifyOk') : t('mis.verifyCancelled')}</b></p>
          <dl className="kv">
            <div><dt>{t('mis.verifyType')}</dt><dd>{t.has(`mis.t_${r.type}`) ? t(`mis.t_${r.type}`) : r.type}</dd></div>
            <div><dt>{t('mis.verifyNumber')}</dt><dd className="mono">{r.numero}</dd></div>
            <div><dt>{t('mis.verifyStatus')}</dt><dd>{t.has(`status.${r.statut}`) ? t(`status.${r.statut}`) : r.statut}</dd></div>
            {r.dateEmission && <div><dt>{t('mis.verifyDate')}</dt><dd className="mono">{r.dateEmission}</dd></div>}
          </dl>
        </div></section>
      ) : <div className="ban bad" role="alert"><div><b>{t('mis.verifyBad')}</b></div></div>}
      <p className="muted">{t('mis.verifyPrivacy')}</p>
    </main>
  );
}

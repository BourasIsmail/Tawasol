'use client';

import { useLocale, useTranslations } from 'next-intl';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate } from '@/lib/format';
import { Icon } from '@/components/Icon';
import { Card, ErrorBox, Loading, Page } from '@/components/ui';

interface Param { cle: string; valeur: string; dateEffet?: string; referenceTexte?: string; statut: string }

export default function Parametres() {
  const t = useTranslations();
  const locale = useLocale();
  const q = useQuery({ queryKey: ['admin-param'], queryFn: () => api<Param[]>('admin/parametres') });
  const label = (k: string) => (t.has(`par.${k.replace(/\./g, '_')}`) ? t(`par.${k.replace(/\./g, '_')}`) : k);
  return (
    <Page title={t('adm.parTitle')} sub={t('adm.parIntro')}>
      {q.isLoading && <Loading />}
      {q.error && <ErrorBox error={q.error} onRetry={() => q.refetch()} />}
      {q.data && (
        <Card flush>
          <div className="scroll">
            <table className="tbl"><thead><tr><th>{t('adm.colParam')}</th><th>{t('adm.colValue')}</th><th>{t('adm.colEffect')}</th><th>{t('adm.colRef')}</th><th>{t('adm.colStatus')}</th></tr></thead>
              <tbody>{q.data.map((p, i) => (
                <tr key={`${p.cle}-${i}`}>
                  <td>{label(p.cle)}</td><td className="mono">{p.valeur}</td><td className="mono">{p.dateEffet ? fmtDate(locale, p.dateEffet) : '—'}</td>
                  <td>{p.referenceTexte && !p.referenceTexte.includes('�') ? p.referenceTexte : t('adm.toFill')}</td>
                  <td>{p.statut === 'valide' ? <span className="pill ok"><Icon name="check" />{t('adm.validated')}</span> : <span className="pill warn"><Icon name="alert" />{t('adm.notValidated')}</span>}</td>
                </tr>
              ))}</tbody></table>
          </div>
        </Card>
      )}
    </Page>
  );
}

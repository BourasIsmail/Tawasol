'use client';

import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate } from '@/lib/format';
import { Icon } from '@/components/Icon';
import { Card, Empty, ErrorBox, Loading, Page, Row } from '@/components/ui';

interface Struct { id: string; code: string; nomFr: string; nomAr: string; parentId?: string; effectif: number }
interface P { id: string; matricule: string; nomFr: string; prenomFr: string; nomAr: string; prenomAr: string; gradeFr?: string; gradeAr?: string; structureFr: string; structureAr: string; dateRecrutement: string; niveau: string }

export default function Staff() {
  const t = useTranslations();
  const locale = useLocale();
  const [sid, setSid] = useState<string | null>(null);
  const [pid, setPid] = useState<string | null>(null);
  const structs = useQuery({ queryKey: ['mystructs'], queryFn: () => api<Struct[]>('structures/mine') });
  const all = structs.data ?? [];
  const ids = new Set(all.map((s) => s.id));
  const depth = (s: Struct): number => { let d = 0; let c: Struct | undefined = s; const seen = new Set<string>(); while (c?.parentId && ids.has(c.parentId) && !seen.has(c.id)) { seen.add(c.id); c = all.find((x) => x.id === c!.parentId); d++; } return d; };
  const ordered: Struct[] = [];
  const byCode = [...all].sort((a, b) => a.code.localeCompare(b.code));
  const walk = (parent?: string) => byCode.filter((s) => (s.parentId && ids.has(s.parentId) ? s.parentId : undefined) === parent).forEach((s) => { ordered.push(s); walk(s.id); });
  walk(undefined);
  const sidEff = sid ?? ordered[0]?.id ?? null;
  const people = useQuery({ queryKey: ['mypeople', sidEff], queryFn: () => api<{ content: P[] }>(`personnel?structureId=${sidEff}&size=100`), enabled: !!sidEff });
  const sel = people.data?.content.find((p) => p.id === pid);
  if (structs.isLoading) return <Loading />;
  if (structs.error) return <ErrorBox error={structs.error} onRetry={() => structs.refetch()} />;
  const nm = (x: { nomFr: string; prenomFr: string; nomAr: string; prenomAr: string }) => (locale === 'ar' ? `${x.prenomAr} ${x.nomAr}` : `${x.prenomFr} ${x.nomFr}`);
  return (
    <Page title={t('rstaff.title')} sub={t('rstaff.sub')}>
      <div className="gs">
        <Card title={t('rstaff.structures')}>
          <ul className="tree">{ordered.map((s) => (
            <li key={s.id} style={{ paddingInlineStart: depth(s) * 16 }}>
              <button type="button" aria-current={sidEff === s.id} onClick={() => { setSid(s.id); setPid(null); }}><Icon name="tree" />{locale === 'ar' ? s.nomAr : s.nomFr}<small>{s.effectif}</small></button>
            </li>
          ))}</ul>
        </Card>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14, minWidth: 0 }}>
          <Card title={t('rstaff.people')} flush>
            {!sidEff && <Empty title={t('rstaff.pick')} />}
            {people.isLoading && <Loading />}
            {people.data && (people.data.content.length === 0 ? <Empty title={t('rstaff.none')} /> : people.data.content.map((p) => (
              <Row key={p.id} main={nm(p)} sub={(locale === 'ar' ? p.gradeAr : p.gradeFr) ?? ''} right={<button type="button" className="btn sm" onClick={() => setPid(p.id)}>{t('rhstaff.sheet')}</button>} />
            )))}
          </Card>
          {sel && (
            <Card title={t('rstaff.fiche')}>
              <b>{nm(sel)}</b>
              <dl className="kv">
                <div><dt>{t('fiche.matricule')}</dt><dd className="mono">{sel.matricule}</dd></div>
                <div><dt>{t('fiche.grade')}</dt><dd>{(locale === 'ar' ? sel.gradeAr : sel.gradeFr) ?? '—'}</dd></div>
                <div><dt>{t('fiche.structure')}</dt><dd>{locale === 'ar' ? sel.structureAr : sel.structureFr}</dd></div>
                <div><dt>{t('fiche.hired')}</dt><dd>{fmtDate(locale, sel.dateRecrutement)}</dd></div>
              </dl>
              <p className="rib"><Icon name="eye" />{t('rstaff.hiddenFin')}</p>
            </Card>
          )}
        </div>
      </div>
    </Page>
  );
}

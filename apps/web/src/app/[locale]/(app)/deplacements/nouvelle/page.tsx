'use client';

import { useEffect, useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useRouter } from 'next/navigation';
import { useMutation, useQuery } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtMoney } from '@/lib/format';
import { Icon } from '@/components/Icon';
import { Card, ErrorBox, Field, Loading, Page } from '@/components/ui';

interface Ref { id: string; code?: string; nomFr?: string; nomAr?: string; libelleFr?: string; libelleAr?: string; tauxKm?: number }
interface Calcul { repasTaux1: number; repasTaux2: number; montantIndemnites: number; fraisTransport: number; montantKm: number; total: number; avertissements: string[] }
interface Droits { saisirPerimetre: boolean }
interface Agent { id: string; nomFr: string; prenomFr: string; matricule: string }

export default function NouvelleMission() {
  const t = useTranslations();
  const locale = useLocale();
  const router = useRouter();
  const [v, setV] = useState<Record<string, string>>({ remboursable: 'oui' });
  const [pour, setPour] = useState<'moi' | 'agent'>('moi');
  const set = (k: string) => (e: { target: { value: string } }) => setV({ ...v, [k]: e.target.value });
  const name = (r: Ref) => (locale === 'ar' ? r.nomAr ?? r.libelleAr : r.nomFr ?? r.libelleFr) ?? '';
  const deleg = useQuery({ queryKey: ['ref-deleg'], queryFn: () => api<Ref[]>('referentiels/delegations') });
  const transport = useQuery({ queryKey: ['ref-transport'], queryFn: () => api<Ref[]>('referentiels/moyens-transport') });
  const programmes = useQuery({ queryKey: ['ref-prog'], queryFn: () => api<Ref[]>('referentiels/programmes-mission') });
  const puissances = useQuery({ queryKey: ['ref-puiss'], queryFn: () => api<Ref[]>('referentiels/puissances-fiscales') });
  const droits = useQuery({ queryKey: ['mission-droits'], queryFn: () => api<Droits>('missions/droits') });
  const equipe = useQuery({ queryKey: ['equipe-agents'], queryFn: () => api<{ content: Agent[] }>('responsable/personnel?size=100'), enabled: !!droits.data?.saisirPerimetre && pour === 'agent' });
  const code = transport.data?.find((x) => x.id === v.transport)?.code;
  const body = () => ({
    personnelId: pour === 'agent' ? v.agent || undefined : undefined, objet: v.objet ?? '', programmeId: v.programme || undefined, departDelegationId: v.depart || undefined, arrivalDelegationId: v.arrival ?? '',
    moyenTransportId: v.transport || undefined, dateDepart: v.d1 ? `${v.d1}:00` : undefined, dateRetour: v.d2 ? `${v.d2}:00` : undefined, remboursable: v.remboursable !== 'non',
    fraisTransport: v.frais ? Number(v.frais) : undefined, vehiculeNom: v.vnom || undefined, vehiculeImmatriculation: v.immat || undefined, puissanceFiscaleId: v.puissance || undefined,
    distanceKm: v.km ? Number(v.km) : undefined, nombreTrajets: v.trajets ? Number(v.trajets) : undefined,
  });
  const sim = useMutation({ mutationFn: () => api<Calcul>('missions/simulation', { method: 'POST', json: body() }) });
  useEffect(() => { if (v.d1 && v.d2 && (pour === 'moi' || v.agent)) { const h = setTimeout(() => sim.mutate(), 400); return () => clearTimeout(h); } }, [v.d1, v.d2, v.transport, v.frais, v.puissance, v.km, v.trajets, v.remboursable, v.agent, pour]); // eslint-disable-line react-hooks/exhaustive-deps
  const save = useMutation({
    mutationFn: async (submit: boolean) => {
      const { id } = await api<{ id: string }>('missions', { method: 'POST', json: body() });
      if (submit) await api(`missions/${id}/soumettre`, { method: 'POST' });
      return id;
    },
    onSuccess: (id) => router.push(`/${locale}/deplacements/${id}`),
  });
  const ready = v.objet && v.arrival && v.d1 && (pour === 'moi' || v.agent);
  const refs = (r: typeof deleg) => r.data?.map((d) => <option key={d.id} value={d.id}>{name(d)}</option>);
  if (deleg.isLoading || transport.isLoading) return <Loading />;
  return (
    <Page title={t('mis.formTitle')} sub={t('mis.formSub')}>
        <Card>
          {droits.data?.saisirPerimetre && (
            <div className="chips" role="group">
              <button type="button" className="chip" aria-pressed={pour === 'moi'} onClick={() => setPour('moi')}>{t('mis.forSelf')}</button>
              <button type="button" className="chip" aria-pressed={pour === 'agent'} onClick={() => setPour('agent')}>{t('mis.forAgent')}</button>
            </div>
          )}
          {pour === 'agent' && <Field label={t('mis.agent')}><select value={v.agent ?? ''} onChange={set('agent')}><option value="" />{equipe.data?.content.map((a) => <option key={a.id} value={a.id}>{a.matricule} · {a.nomFr} {a.prenomFr}</option>)}</select></Field>}
          <Field label={t('mis.object')}><input value={v.objet ?? ''} onChange={set('objet')} placeholder={t('mis.objectPh')} /></Field>
          <div className="ff c2">
            <Field label={t('mis.depart')}><select value={v.depart ?? ''} onChange={set('depart')}><option value="" />{refs(deleg)}</select></Field>
            <Field label={t('mis.arrival')}><select value={v.arrival ?? ''} onChange={set('arrival')}><option value="" />{refs(deleg)}</select></Field>
            <Field label={t('mis.dateDepart')}><input type="datetime-local" value={v.d1 ?? ''} onChange={set('d1')} /></Field>
            <Field label={t('mis.dateRetour')}><input type="datetime-local" value={v.d2 ?? ''} onChange={set('d2')} /></Field>
            <Field label={t('mis.programme')}><select value={v.programme ?? ''} onChange={set('programme')}><option value="" />{refs(programmes)}</select></Field>
            <Field label={t('mis.remboursable')}><select value={v.remboursable} onChange={set('remboursable')}><option value="oui">{t('common.yes')}</option><option value="non">{t('common.no')}</option></select></Field>
          </div>
          <div className="tabs" role="tablist" aria-label={t('mis.transport')}>{transport.data?.map((x) => <button key={x.id} type="button" role="tab" aria-pressed={v.transport === x.id} onClick={() => setV({ ...v, transport: x.id })}>{name(x)}</button>)}</div>
          {code === 'transports_en_commun' && <div className="ff c2"><Field label={t('mis.fraisTransport')}><input type="number" min="0" step="0.01" value={v.frais ?? ''} onChange={set('frais')} /></Field></div>}
          {code === 'voiture_privee' && (
            <div className="ff c2">
              <Field label={t('mis.vehicule')}><input value={v.vnom ?? ''} onChange={set('vnom')} /></Field>
              <Field label={t('mis.immatriculation')}><input value={v.immat ?? ''} onChange={set('immat')} /></Field>
              <Field label={t('mis.puissance')}><select value={v.puissance ?? ''} onChange={set('puissance')}><option value="" />{puissances.data?.map((p) => <option key={p.id} value={p.id}>{name(p)} ({p.tauxKm} {t('common.mad')}/km)</option>)}</select></Field>
              <Field label={t('mis.distance')}><input type="number" min="0" step="0.1" value={v.km ?? ''} onChange={set('km')} /></Field>
              <Field label={t('mis.trajets')}><input type="number" min="1" value={v.trajets ?? ''} onChange={set('trajets')} /></Field>
            </div>
          )}
          {save.error != null && <ErrorBox error={save.error} />}
          {sim.error != null && <ErrorBox error={sim.error} />}
          {sim.data ? (
            <>
              <dl className="kv">
                <div><dt>{t('mis.mealsLine')}</dt><dd className="mono">{sim.data.repasTaux1} × · {sim.data.repasTaux2} ×</dd></div>
                <div><dt>{t('mis.indemnities')}</dt><dd className="mono">{fmtMoney(locale, sim.data.montantIndemnites)} {t('common.mad')}</dd></div>
                <div><dt>{t('mis.fees')}</dt><dd className="mono">{fmtMoney(locale, sim.data.fraisTransport)} {t('common.mad')}</dd></div>
                <div><dt>{t('mis.km')}</dt><dd className="mono">{fmtMoney(locale, sim.data.montantKm)} {t('common.mad')}</dd></div>
              </dl>
              <div className="ban ok"><div><b>{t('mis.totalPrev')} : {fmtMoney(locale, sim.data.total)} {t('common.mad')}</b></div></div>
              {sim.data.avertissements.map((a) => <div key={a} className="ban warn"><Icon name="alert" /><div><small>{t.has(`mis.w_${a}`) ? t(`mis.w_${a}`) : a}</small></div></div>)}
            </>
          ) : <p className="muted">{t('mis.estimateHint')}</p>}
          <div className="ban info"><Icon name="shield" /><div><small>{t('mis.checks')}</small></div></div>
        </Card>
        <div className="acts">
          <button type="button" className="btn" disabled={!ready || save.isPending} onClick={() => save.mutate(false)}><Icon name="pencil" />{t('mis.saveDraft')}</button>
          <button type="button" className="btn pri" disabled={!ready || save.isPending} onClick={() => save.mutate(true)}><Icon name="send" />{t('dem.submit')}</button>
        </div>
    </Page>
  );
}

'use client';

import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/client';
import { fmtDate } from '@/lib/format';
import { Icon } from '@/components/Icon';
import { Card, Empty, ErrorBox, Field, Loading, Page, Row, WebOnly } from '@/components/ui';

interface Camp { annee: number; dateOuverture: string; dateCloture: string; baremeMax: number }
interface Note { personnelId: string; nomFr: string; prenomFr: string; nomAr: string; prenomAr: string; structureFr: string; note: number; appreciation?: string; saisiePar?: string }
interface Taux { structureId: string; code: string; nomFr: string; nomAr: string; agents: number; notes: number }

export default function RhNotation() {
  const t = useTranslations();
  const locale = useLocale();
  const qc = useQueryClient();
  const [camp, setCamp] = useState({ annee: String(new Date().getFullYear()), dateOuverture: '', dateCloture: '' });
  const [sending, setSending] = useState<string | null>(null);
  const [reason, setReason] = useState('');
  const [published, setPublished] = useState<number | null>(null);
  const camps = useQuery({ queryKey: ['rhcamps'], queryFn: () => api<Camp[]>('rh/notation/campagnes') });
  const annee = camps.data?.[0]?.annee;
  const notes = useQuery({ queryKey: ['rhnotes', annee], queryFn: () => api<Note[]>(`rh/notation/${annee}/notes`), enabled: !!annee });
  const taux = useQuery({ queryKey: ['rhtaux', annee], queryFn: () => api<Taux[]>(`rh/notation/${annee}/taux`), enabled: !!annee });
  const refresh = () => { qc.invalidateQueries({ queryKey: ['rhnotes'] }); qc.invalidateQueries({ queryKey: ['rhtaux'] }); qc.invalidateQueries({ queryKey: ['rhdash'] }); setSending(null); setReason(''); };
  const open = useMutation({ mutationFn: () => api('rh/notation/campagnes', { method: 'POST', json: { annee: Number(camp.annee), dateOuverture: camp.dateOuverture, dateCloture: camp.dateCloture } }), onSuccess: () => qc.invalidateQueries({ queryKey: ['rhcamps'] }) });
  const validate = useMutation({ mutationFn: (pid: string) => api(`rh/notation/${annee}/notes/${pid}/valider`, { method: 'POST' }), onSuccess: refresh });
  const back = useMutation({ mutationFn: (pid: string) => api(`rh/notation/${annee}/notes/${pid}/renvoyer`, { method: 'POST', json: { commentaire: reason } }), onSuccess: refresh });
  const publish = useMutation({ mutationFn: () => api<{ publiees: number }>(`rh/notation/${annee}/publier`, { method: 'POST' }), onSuccess: (r) => { setPublished(r.publiees); refresh(); } });
  const nm = (x: Note) => (locale === 'ar' ? `${x.prenomAr} ${x.nomAr}` : `${x.prenomFr} ${x.nomFr}`);
  if (camps.isLoading) return <Loading />;
  if (camps.error) return <ErrorBox error={camps.error} onRetry={() => camps.refetch()} />;
  const c = camps.data?.[0];
  return (
    <WebOnly>
      <Page title={c ? `${t('rhnota.title')} ${c.annee}` : t('rhnota.title')} sub={c ? t('rhnota.openFromTo', { from: fmtDate(locale, c.dateOuverture), to: fmtDate(locale, c.dateCloture) }) : undefined}
        actions={c ? <button type="button" className="btn pri" disabled={publish.isPending} onClick={() => publish.mutate()}>{t('rhnota.publish')}</button> : undefined}>
        {published != null && <div className="ban ok" role="status"><div><b>{t('rhnota.published', { n: published })}</b></div></div>}
        {!c && <Card title={t('rhnota.open')}>
          <p className="muted" style={{ margin: 0 }}>{t('rhnota.noCampaign')}</p>
          <div className="ff c2"><Field label={t('rhnota.year')}><input type="number" value={camp.annee} onChange={(e) => setCamp({ ...camp, annee: e.target.value })} /></Field><span />
            <Field label={t('rhnota.from')}><input type="date" value={camp.dateOuverture} onChange={(e) => setCamp({ ...camp, dateOuverture: e.target.value })} /></Field>
            <Field label={t('rhnota.to')}><input type="date" value={camp.dateCloture} onChange={(e) => setCamp({ ...camp, dateCloture: e.target.value })} /></Field></div>
          {open.error && <ErrorBox error={open.error} />}
          <button type="button" className="btn pri" disabled={!camp.dateOuverture || !camp.dateCloture || open.isPending} onClick={() => open.mutate()}>{t('rhnota.open')}</button>
        </Card>}
        {c && <>
          {(validate.error || back.error || publish.error) && <ErrorBox error={validate.error ?? back.error ?? publish.error} />}
          <Card title={t('rhnota.rate')}>
            <div className="bars">{taux.data?.map((x) => { const p = x.agents ? Math.round((100 * Number(x.notes)) / x.agents) : 0; return <div key={x.structureId}><span>{locale === 'ar' ? x.nomAr : x.nomFr}</span><div className="meter"><i style={{ width: `${p}%` }} /></div><b>{p}%</b></div>; })}</div>
          </Card>
          <Card title={t('rhnota.toCheck')} flush>
            {notes.data?.length ? notes.data.map((n) => (
              <div key={n.personnelId}>
                <Row main={`${nm(n)} · ${n.note} / ${c.baremeMax}`} sub={`${n.structureFr}${n.saisiePar ? ` · ${t('rhnota.by', { name: n.saisiePar })}` : ''}${n.appreciation ? ` · ${n.appreciation}` : ''}`}
                  right={<><button type="button" className="btn sm" onClick={() => setSending(n.personnelId)}>{t('rhnota.send')}</button><button type="button" className="btn sm ok" disabled={validate.isPending} onClick={() => validate.mutate(n.personnelId)}><Icon name="check" />{t('rhnota.validate')}</button></>} />
                {sending === n.personnelId && <div className="cb"><Field label={t('rhnota.sendReason')}><textarea value={reason} onChange={(e) => setReason(e.target.value)} /></Field><div className="acts"><button type="button" className="btn bad" disabled={!reason.trim() || back.isPending} onClick={() => back.mutate(n.personnelId)}>{t('rhnota.send')}</button><button type="button" className="btn" onClick={() => setSending(null)}>{t('common.cancel')}</button></div></div>}
              </div>
            )) : <Empty title={t('rhnota.none')} />}
          </Card>
        </>}
      </Page>
    </WebOnly>
  );
}

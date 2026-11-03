'use client';

import type { ReactNode } from 'react';
import { useTranslations } from 'next-intl';
import { ApiRequestError } from '@/lib/client';

export function Card({ title, action, children, flush }: { title?: ReactNode; action?: ReactNode; children: ReactNode; flush?: boolean }) {
  return (
    <section className="panel">
      {title && <header><h3>{title}</h3>{action}</header>}
      <div className={flush ? 'cb flush' : 'cb'}>{children}</div>
    </section>
  );
}

export function Page({ title, sub, actions, children }: { title: ReactNode; sub?: ReactNode; actions?: ReactNode; children: ReactNode }) {
  return (
    <>
      <div className="ph">
        <div><h2>{title}</h2>{sub && <p className="sub">{sub}</p>}</div>
        {actions && <div className="acts">{actions}</div>}
      </div>
      {children}
    </>
  );
}

const TONE: Record<string, string> = {
  brouillon: 'n', soumise: 'i', validee_responsable: 't', approuvee: 'ok', finalisee: 't', payee: 'ok', rejetee_responsable: 'bad', rejetee: 'bad', annulee: 'n',
  nouvelle: 'i', en_cours: 'warn', resolue: 'ok',
};
const MARK: Record<string, string> = { approuvee: '✓', finalisee: '✓', payee: '€', validee_responsable: '✓', resolue: '✓', rejetee: '✕', rejetee_responsable: '✕', annulee: '⊘', brouillon: '✎', soumise: '➤' };

/** Le statut est dit par le texte, une icône et la couleur : jamais par la couleur seule. */
export function Pill({ status, label }: { status: string; label: string }) {
  return <span className={`pill ${TONE[status] ?? 'n'}`}><span aria-hidden="true">{MARK[status] ?? '•'}</span>{label}</span>;
}

export function Row({ main, sub, right, href, onClick }: { main: ReactNode; sub?: ReactNode; right?: ReactNode; href?: string; onClick?: () => void }) {
  const body = (
    <>
      <div className="rm"><b>{main}</b>{sub && <small>{sub}</small>}</div>
      {right && <div className="rr">{right}</div>}
    </>
  );
  if (href) return <a className="rw link" href={href}>{body}</a>;
  return <div className="rw" onClick={onClick}>{body}</div>;
}

export function Loading() {
  const t = useTranslations('common');
  return <div className="sk-wrap" role="status" aria-live="polite"><div className="sk" style={{ width: '60%' }} /><div className="sk" /><div className="sk" style={{ width: '80%' }} /><span className="sr">{t('loading')}</span></div>;
}

export function errorMessage(t: ReturnType<typeof useTranslations>, err: unknown): string {
  const code = err instanceof ApiRequestError ? err.error.code : 'generic';
  return t.has(`errors.${code}`) ? t(`errors.${code}`) : t('errors.generic');
}

export function ErrorBox({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const t = useTranslations();
  return (
    <div className="ban bad" role="alert">
      <div><b>{t('common.error')}</b><small>{errorMessage(t, error)}</small></div>
      {onRetry && <button type="button" className="btn sm" onClick={onRetry}>{t('common.retry')}</button>}
    </div>
  );
}

export function Empty({ title, hint, action }: { title: string; hint?: string; action?: ReactNode }) {
  return <div className="empty"><b>{title}</b>{hint && <span>{hint}</span>}{action}</div>;
}

export function Field({ label, children, hint }: { label: string; children: ReactNode; hint?: string }) {
  return <label className="fld"><span>{label}</span>{children}{hint && <small>{hint}</small>}</label>;
}

/** Écran réservé au web : sur téléphone, un message remplace le contenu. */
export function WebOnly({ children }: { children: ReactNode }) {
  const t = useTranslations('nav3');
  return <><div className="ban webonly-note"><div><b>{t('webOnly')}</b></div></div><div className="webonly">{children}</div></>;
}

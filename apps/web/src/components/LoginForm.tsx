'use client';

import { useEffect, useState, type FormEvent } from 'react';
import { useLocale, useTranslations } from 'next-intl';

export function LoginForm() {
  const t = useTranslations();
  const locale = useLocale();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [ldap, setLdap] = useState(false);
  useEffect(() => { fetch('/api/bff/auth/config').then((r) => (r.ok ? r.json() : null)).then((c) => setLdap(Boolean(c?.ldap))).catch(() => undefined); }, []);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    setBusy(true);
    setError(null);
    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ login: String(data.get('login')), password: String(data.get('password')) }),
      });
      if (!res.ok) {
        const body = (await res.json().catch(() => null)) as { code?: string } | null;
        setError(body?.code && t.has(`errors.${body.code}`) ? body.code : 'generic');
        return;
      }
      const { mustChangePassword, role } = (await res.json()) as { mustChangePassword: boolean; role: string };
      window.location.href = mustChangePassword ? `/${locale}/change-password` : `/${locale}/${role === 'responsable' ? 'responsable/dashboard' : role === 'rh' ? 'rh/dashboard' : 'dashboard'}`;
    } catch {
      setError('generic');
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={onSubmit} style={{ display: 'contents' }} noValidate>
      <label><span>{t('login.login')}</span><input name="login" autoComplete="username" required /></label>
      <label><span>{t('login.password')}</span><input name="password" type="password" autoComplete="current-password" required /></label>
      {error && <p role="alert" className="err">{t(`errors.${error}`)}</p>}
      <button type="submit" className="btn pri full" disabled={busy}>{t('login.submit')}</button>
      {ldap
        ? <small className="muted" style={{ textAlign: 'center' }}>{t('login.ldapNote')}</small>
        : <a href={`/${locale}/forgot`} style={{ textAlign: 'center', color: 'var(--brand-text)' }}>{t('login.forgot')}</a>}
      <small className="muted" style={{ textAlign: 'center' }}>{t('login.lockNote')}</small>
    </form>
  );
}

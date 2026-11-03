'use client';

import { useState, type FormEvent } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import Link from 'next/link';
import { Icon } from '@/components/Icon';
import { AuthCard } from '@/components/AuthCard';

export default function Forgot() {
  const t = useTranslations();
  const locale = useLocale();
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(false);
  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true); setError(false);
    const login = String(new FormData(e.currentTarget).get('login'));
    const res = await fetch('/api/bff/auth/forgot-password', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ login }) }).catch(() => null);
    setBusy(false);
    if (res && (res.ok || res.status === 204)) setSent(true); else setError(true);
  }
  return (
    <AuthCard title={t('auth.forgotTitle')}>
      {sent ? <p role="status">{t('auth.sentNeutral')}</p> : (
        <form onSubmit={onSubmit} style={{ display: 'contents' }}>
          <p className="muted" style={{ margin: 0 }}>{t('auth.forgotText')}</p>
          <label><span>{t('login.login')}</span><input name="login" autoComplete="username" required /></label>
          {error && <p className="error" role="alert">{t('errors.generic')}</p>}
          <button type="submit" disabled={busy}><Icon name="send" />{t('auth.send')}</button>
        </form>
      )}
      <Link href={`/${locale}/login`} className="muted" style={{ textAlign: 'center' }}>{t('auth.back')}</Link>
    </AuthCard>
  );
}

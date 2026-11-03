'use client';

import { useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { AuthCard, NewPassword } from '@/components/AuthCard';

export default function ChangePassword() {
  const t = useTranslations();
  const locale = useLocale();
  const [current, setCurrent] = useState('');
  const [state, setState] = useState({ valid: false, value: '' });
  const [error, setError] = useState<string | null>(null);
  async function submit() {
    setError(null);
    const res = await fetch('/api/auth/change-password', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ currentPassword: current, newPassword: state.value }) });
    if (res.ok) {
      const { role } = (await res.json()) as { role: string };
      window.location.href = `/${locale}/${role === 'responsable' ? 'responsable/dashboard' : role === 'rh' ? 'rh/dashboard' : 'dashboard'}`;
      return;
    }
    if (res.status === 401) { window.location.href = `/${locale}/login`; return; }
    const body = (await res.json().catch(() => null)) as { code?: string } | null;
    setError(body?.code && t.has(`errors.${body.code}`) ? body.code : 'generic');
  }
  return (
    <AuthCard title={t('auth.changeTitle')}>
      <p className="muted" style={{ margin: 0 }}>{t('auth.changeText')}</p>
      <label><span>{t('auth.current')}</span><input type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} /></label>
      <NewPassword onChange={(valid, value) => setState({ valid, value })} />
      {error && <p className="error" role="alert">{t(`errors.${error}`)}</p>}
      <button type="button" disabled={!state.valid || !current} onClick={submit}>{t('auth.save')}</button>
    </AuthCard>
  );
}

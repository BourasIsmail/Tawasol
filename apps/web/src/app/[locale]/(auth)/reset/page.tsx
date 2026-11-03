'use client';

import { Suspense, useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { AuthCard, NewPassword } from '@/components/AuthCard';

function Form() {
  const t = useTranslations();
  const locale = useLocale();
  const token = useSearchParams().get('token') ?? '';
  const [state, setState] = useState({ valid: false, value: '' });
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);
  async function submit() {
    setError(null);
    const res = await fetch('/api/bff/auth/reset-password', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token, newPassword: state.value }) });
    if (res.ok) { setDone(true); return; }
    const body = (await res.json().catch(() => null)) as { code?: string } | null;
    setError(body?.code && t.has(`errors.${body.code}`) ? body.code : 'generic');
  }
  return (
    <AuthCard title={t('auth.resetTitle')}>
      {done ? <p role="status">{t('auth.done')}</p> : (
        <>
          <NewPassword onChange={(valid, value) => setState({ valid, value })} />
          {error && <p className="error" role="alert">{t(`errors.${error}`)}</p>}
          <button type="button" disabled={!state.valid || !token} onClick={submit}>{t('auth.save')}</button>
        </>
      )}
      <Link href={`/${locale}/login`} className="muted" style={{ textAlign: 'center' }}>{t('auth.back')}</Link>
    </AuthCard>
  );
}

export default function Reset() { return <Suspense><Form /></Suspense>; }

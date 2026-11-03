'use client';

import { useState, type ReactNode } from 'react';
import { useTranslations } from 'next-intl';

export function AuthCard({ title, children }: { title: string; children: ReactNode }) {
  const t = useTranslations();
  return (
    <main className="login-main">
      <div style={{ width: 'min(400px,100%)', display: 'flex', flexDirection: 'column', gap: 16 }}>
        <header style={{ textAlign: 'center' }}>
          <h1 style={{ margin: 0 }}>{t('app.name')}</h1>
          <p className="muted" style={{ margin: 0 }}>{title}</p>
        </header>
        <div className="card">{children}</div>
      </div>
    </main>
  );
}

export function policyChecks(p: string) {
  return [['p_len', p.length >= 12], ['p_upper', /[A-Z]/.test(p)], ['p_lower', /[a-z]/.test(p)], ['p_digit', /\d/.test(p)], ['p_symbol', /[^A-Za-z0-9]/.test(p)]] as const;
}

/** Nouveau mot de passe avec règles affichées en direct (la vérification faisant foi reste celle du serveur). */
export function NewPassword({ onChange }: { onChange: (valid: boolean, value: string) => void }) {
  const t = useTranslations('auth');
  const [p, setP] = useState('');
  const [c, setC] = useState('');
  const checks = policyChecks(p);
  const update = (np: string, nc: string) => { setP(np); setC(nc); onChange(policyChecks(np).every(([, ok]) => ok) && np === nc, np); };
  return (
    <>
      <label><span>{t('newPassword')}</span><input type="password" autoComplete="new-password" value={p} onChange={(e) => update(e.target.value, c)} /></label>
      <ul className="pol" aria-live="polite">{checks.map(([k, ok]) => <li key={k} className={ok ? 'y' : ''}><span aria-hidden="true">{ok ? '✓' : '○'}</span>{t(k)}</li>)}</ul>
      <label><span>{t('confirm')}</span><input type="password" autoComplete="new-password" value={c} onChange={(e) => update(p, e.target.value)} /></label>
      {c && p !== c && <p className="error" role="alert">{t('mismatch')}</p>}
      <small className="muted">{t('policyNote')}</small>
    </>
  );
}

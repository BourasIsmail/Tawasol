import { getTranslations, setRequestLocale } from 'next-intl/server';
import Link from 'next/link';
import { LoginForm } from '@/components/LoginForm';

export default async function LoginPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  setRequestLocale(locale);
  const t = await getTranslations();
  return (
    <main className="login-main">
      <div style={{ width: 'min(400px,100%)', display: 'flex', flexDirection: 'column', gap: 16 }}>
        <header style={{ textAlign: 'center' }}>
          <h1 style={{ margin: 0 }}>{t('app.name')}</h1>
          <p className="muted" style={{ margin: 0 }}>{t('app.tagline')}</p>
        </header>
        <LoginForm />
        <nav className="lang" aria-label="Langue">
          <Link href="/fr/login" hrefLang="fr">{t('language.fr')}</Link>
          <Link href="/ar/login" hrefLang="ar">{t('language.ar')}</Link>
        </nav>
      </div>
    </main>
  );
}

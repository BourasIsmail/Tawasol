import { getTranslations, setRequestLocale } from 'next-intl/server';
import { AuthShell } from '@/components/AuthShell';
import { LoginForm } from '@/components/LoginForm';

export default async function LoginPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  setRequestLocale(locale);
  const t = await getTranslations();
  return (
    <AuthShell title={t('login.title')} subtitle={t('app.tagline')}>
      <LoginForm />
    </AuthShell>
  );
}

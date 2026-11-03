// Dates et montants au format marocain (jj/mm/aaaa, chiffres latins) dans les deux langues ; seuls les noms de mois suivent la langue.
const tag = (locale: string) => (locale === 'ar' ? 'ar-MA-u-nu-latn' : 'fr-FR');
const num = 'fr-FR';

export const fmtDate = (locale: string, iso?: string | null): string =>
  iso ? new Intl.DateTimeFormat(num, { day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'Africa/Casablanca' }).format(new Date(iso)) : '—';

export const fmtDateTime = (locale: string, iso?: string | null): string =>
  iso ? new Intl.DateTimeFormat(num, { dateStyle: 'short', timeStyle: 'short', timeZone: 'Africa/Casablanca' }).format(new Date(iso)) : '—';

export const fmtMoney = (locale: string, n?: number | null): string =>
  n == null ? '—' : new Intl.NumberFormat(num, { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(n);

export const fmtMonth = (locale: string, year: number, month: number): string =>
  new Intl.DateTimeFormat(tag(locale), { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(new Date(Date.UTC(year, month - 1, 1)));

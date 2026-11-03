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

/** « Lundi 2 novembre 2026 » : jour de la semaine et mois dans la langue de l'interface, première lettre en capitale. */
export const fmtLongDate = (locale: string, d: Date = new Date()): string => {
  const s = new Intl.DateTimeFormat(locale === 'ar' ? 'ar-MA-u-nu-latn' : 'fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: 'Africa/Casablanca' }).format(d);
  return s.charAt(0).toUpperCase() + s.slice(1);
};

/** « Oct. 2026 » : mois abrégé et année. */
export const fmtMonthShort = (locale: string, year: number, month: number): string => {
  const s = new Intl.DateTimeFormat(locale === 'ar' ? 'ar-MA-u-nu-latn' : 'fr-FR', { month: 'short', year: 'numeric', timeZone: 'UTC' }).format(new Date(Date.UTC(year, month - 1, 1)));
  return s.charAt(0).toUpperCase() + s.slice(1);
};

/** Prénom en capitales de la base (« FOUZIA ») -> « Fouzia ». */
export const cap = (s?: string | null): string => (s ? s.toLowerCase().replace(/(^|[\s-])(\p{L})/gu, (_, a, b) => a + b.toUpperCase()) : '');

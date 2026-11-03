import { defineRouting } from 'next-intl/routing';
import { LOCALES } from '@tawassol/shared';

export const routing = defineRouting({ locales: [...LOCALES], defaultLocale: 'fr' });

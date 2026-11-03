/** Constantes partagées entre le web et le mobile. Les statuts reflètent les ENUM de la base. */
export const LOCALES = ['fr', 'ar'] as const;
export type Locale = (typeof LOCALES)[number];
export const isRtl = (locale: string): boolean => locale === 'ar';

export const ROLES = ['agent', 'responsable', 'rh', 'admin'] as const;
export type Role = (typeof ROLES)[number];

export const DEMANDE_STATUTS = ['brouillon', 'soumise', 'validee_responsable', 'rejetee_responsable', 'approuvee', 'rejetee', 'annulee'] as const;
export type DemandeStatut = (typeof DEMANDE_STATUTS)[number];

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  mustChangePassword: boolean;
  role: Role;
}

export interface ApiError {
  status: number;
  code: string;
  message: string;
  details?: string[];
}
export { messages } from './messages';

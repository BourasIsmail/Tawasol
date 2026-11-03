'use client';

import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/client';

export interface Mission {
  id: string; personnelId: string; agentNom: string; matricule: string; objet: string; statut: string; numeroOrdm?: string; numeroEtat?: string; numeroOrdmLegacy?: string; numeroEtatLegacy?: string;
  dateDepart: string; dateRetour?: string; arrivalDelegation?: string; montantTotal: number; historiqueNonReproductible: boolean; avertissements?: string[]; actionsPossibles: string[];
}
export interface Droits { demander: boolean; saisirPerimetre: boolean; validerNiveau1: boolean; valider: boolean; validerPaiement: boolean; etats: boolean; analytics: boolean }

export const MISSION_STATUTS = ['brouillon', 'soumise', 'validee_responsable', 'approuvee', 'finalisee', 'payee', 'rejetee', 'annulee'];
export const useDroits = () => useQuery({ queryKey: ['mission-droits'], queryFn: () => api<Droits>('missions/droits') });


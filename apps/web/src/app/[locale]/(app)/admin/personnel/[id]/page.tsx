'use client';

import { use } from 'react';
import { PersonnelForm } from '@/components/PersonnelForm';
import { WebOnly } from '@/components/ui';

/** Fiche d'un agent côté administrateur : identité et dossier RH (jamais la paie), modifications journalisées comme pour la RH. */
export default function FicheAdmin({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return <WebOnly><PersonnelForm id={id} scope="admin" /></WebOnly>;
}

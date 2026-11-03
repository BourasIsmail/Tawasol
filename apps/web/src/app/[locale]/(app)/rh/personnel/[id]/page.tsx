'use client';

import { use } from 'react';
import { PersonnelForm } from '@/components/PersonnelForm';
import { WebOnly } from '@/components/ui';

export default function Fiche({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return <WebOnly><PersonnelForm id={id} /></WebOnly>;
}

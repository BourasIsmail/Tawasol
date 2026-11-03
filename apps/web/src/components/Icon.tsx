import { ICONS } from './icons';

/** Icône de la maquette. Décorative : le sens est toujours porté par le texte voisin. */
export function Icon({ name, className }: { name: string; className?: string }) {
  return <svg className={`ic${className ? ` ${className}` : ''}`} viewBox="0 0 24 24" aria-hidden="true" dangerouslySetInnerHTML={{ __html: ICONS[name] ?? '' }} />;
}

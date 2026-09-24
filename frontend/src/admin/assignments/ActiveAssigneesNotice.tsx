import { Link } from 'react-router';
import { Alert } from '../../shared/components/Alert';
import { usePlanAssignments } from './api';

/** Spec 10.3: before editing a shared plan show how many active assignees are involved. */
export function ActiveAssigneesNotice({ planId }: { planId: string }) {
  const { data } = usePlanAssignments(planId);
  if (!data) {
    return null;
  }
  const active = data.filter((a) => a.status === 'ACTIVE').length;
  if (active === 0) {
    return (
      <Alert tone="info">
        <p>
          Nessun utente ha questa scheda attiva. <Link to={`/admin/plans/${planId}/assignments`}>Gestisci assegnazioni</Link>
        </p>
      </Alert>
    );
  }
  return (
    <Alert tone="warning" title={`Scheda condivisa: ${active} ${active === 1 ? 'utente attivo' : 'utenti attivi'}`}>
      <p>
        Le modifiche valgono per tutti gli assegnatari dagli allenamenti non ancora avviati. Gli allenamenti già iniziati o
        conclusi non cambiano. Per personalizzare la scheda per un solo utente, duplicala.{' '}
        <Link to={`/admin/plans/${planId}/assignments`}>Vedi assegnatari</Link>
      </p>
    </Alert>
  );
}

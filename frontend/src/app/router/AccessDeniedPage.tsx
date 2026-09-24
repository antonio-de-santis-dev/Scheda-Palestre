import { Link } from 'react-router';
import { ShieldAlert } from 'lucide-react';
import { EmptyState } from '../../shared/components/States';

export function AccessDeniedPage() {
  return (
    <main className="main main--narrow">
      <EmptyState title="Accesso negato" icon={<ShieldAlert size={40} />}>
        <p>Non hai i permessi per visualizzare questa pagina.</p>
        <Link to="/" className="btn btn--primary">
          Torna alla home
        </Link>
      </EmptyState>
    </main>
  );
}

export function NotFoundPage() {
  return (
    <main className="main main--narrow">
      <EmptyState title="Pagina non trovata">
        <p>La pagina richiesta non esiste.</p>
        <Link to="/" className="btn btn--primary">
          Torna alla home
        </Link>
      </EmptyState>
    </main>
  );
}

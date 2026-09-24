import { Link } from 'react-router';
import { QueryState } from '../../shared/components/States';
import { formatDate } from '../../shared/utils/format';
import { useUserAssignments } from './api';
import { AssignmentStatusBadge } from './AssignmentStatusBadge';

export function UserAssignmentsSection({ userId }: { userId: string }) {
  const query = useUserAssignments(userId);
  return (
    <section className="card" aria-labelledby="user-assignments-title">
      <h2 id="user-assignments-title" className="card__title">
        Schede assegnate
      </h2>
      <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
        {query.data && query.data.length === 0 ? (
          <p className="muted">Nessuna scheda assegnata. Assegnala dalla pagina della scheda.</p>
        ) : (
          <ul className="list">
            {query.data?.map((a) => (
              <li key={a.id} className="list-item">
                <div className="list-item__main">
                  <div className="list-item__title">
                    {a.planDeleted ? a.planName : <Link to={`/admin/plans/${a.planId}/assignments`}>{a.planName}</Link>}
                  </div>
                  <div className="list-item__meta">
                    dal {formatDate(a.startDate)}
                    {a.endDate ? ` al ${formatDate(a.endDate)}` : ''}
                    {a.planDeleted ? ' · scheda eliminata' : ''}
                  </div>
                </div>
                <AssignmentStatusBadge status={a.status} />
              </li>
            ))}
          </ul>
        )}
      </QueryState>
    </section>
  );
}

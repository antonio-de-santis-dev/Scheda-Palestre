import { Link } from 'react-router';
import { ClipboardList } from 'lucide-react';
import { PageHeader } from '../../shared/components/PageHeader';
import { EmptyState, QueryState } from '../../shared/components/States';
import { formatDate } from '../../shared/utils/format';
import { AssignmentStatusBadge } from '../../admin/assignments/AssignmentStatusBadge';
import { useMyAssignments } from './api';

export function MyPlansPage() {
  const query = useMyAssignments();
  const sorted = [...(query.data ?? [])].sort((a, b) => Number(b.active) - Number(a.active));
  return (
    <>
      <PageHeader title="Le mie schede" subtitle="Schede ricevute dalla palestra, in sola lettura." />
      <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
        {sorted.length === 0 ? (
          <EmptyState title="Nessuna scheda assegnata" icon={<ClipboardList size={40} />}>
            <p>Quando la palestra ti assegnerà una scheda la troverai qui.</p>
          </EmptyState>
        ) : (
          <ul className="list" aria-label="Schede assegnate">
            {sorted.map((a) => (
              <li key={a.id} className="list-item">
                <div className="list-item__main">
                  <div className="list-item__title">
                    <Link to={`/app/plans/${a.id}`}>{a.planName}</Link>
                  </div>
                  <div className="list-item__meta">
                    dal {formatDate(a.startDate)}
                    {a.endDate ? ` al ${formatDate(a.endDate)}` : ''}
                  </div>
                </div>
                <AssignmentStatusBadge status={a.status} />
              </li>
            ))}
          </ul>
        )}
      </QueryState>
    </>
  );
}

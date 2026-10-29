import { Link } from 'react-router';
import { PageHeader } from '../shared/components/PageHeader';
import { useCurrentUser } from '../auth/useAuth';
import { ADMIN_SHORTCUTS } from '../app/router/navigation';
import { useUsers } from './users/api';
import { usePlans } from './plans/api';
import { useExercises, useMuscleGroups } from './catalog/api';
import { QueryState } from '../shared/components/States';

/** Counts come from paginated totals, fetching only one record per catalog. */
export function AdminDashboardPage() {
  const { data: user } = useCurrentUser();
  const users = useUsers({ active: true, role: 'USER', size: 1 });
  const plans = usePlans({ deleted: false, size: 1 });
  const groups = useMuscleGroups({ active: true, size: 1 });
  const exercises = useExercises({ active: true, size: 1 });
  const metrics = [
    { label: 'Utenti attivi', query: users },
    { label: 'Schede disponibili', query: plans },
    { label: 'Gruppi muscolari attivi', query: groups },
    { label: 'Esercizi attivi', query: exercises },
  ];
  return (
    <>
      <PageHeader title="Dashboard" subtitle={user ? `Ciao ${user.firstName}, cosa vuoi gestire?` : undefined} />
      <div className="dashboard-metrics">
        {metrics.map(({ label, query }) => <section className="card dashboard-metric" key={label} aria-label={label}>
          <p className="muted small">{label}</p>
          <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
            <strong>{query.data?.totalElements.toLocaleString('it-IT')}</strong>
          </QueryState>
        </section>)}
      </div>
      <ul className="dashboard-shortcuts list" aria-label="Collegamenti rapidi">
        {ADMIN_SHORTCUTS.filter((s) => s.to !== '/admin/profile').map(({ to, label, description, icon: Icon }) => (
          <li key={to}>
            <Link to={to} className="card" style={{ display: 'flex', gap: 'var(--space-3)', textDecoration: 'none', color: 'inherit', height: '100%' }}>
              <span className="state__icon">
                <Icon size={28} aria-hidden={true} />
              </span>
              <span>
                <strong className="card__title" style={{ display: 'block' }}>
                  {label}
                </strong>
                <span className="muted">{description}</span>
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </>
  );
}

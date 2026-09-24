import { Link } from 'react-router';
import { PageHeader } from '../shared/components/PageHeader';
import { useCurrentUser } from '../auth/useAuth';
import { ADMIN_SHORTCUTS } from '../app/router/navigation';

/** Quick links only: no invented statistics (spec 14.3). */
export function AdminDashboardPage() {
  const { data: user } = useCurrentUser();
  return (
    <>
      <PageHeader title="Dashboard" subtitle={user ? `Ciao ${user.firstName}, cosa vuoi gestire?` : undefined} />
      <ul className="grid list" aria-label="Collegamenti rapidi">
        {ADMIN_SHORTCUTS.map(({ to, label, description, icon: Icon }) => (
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

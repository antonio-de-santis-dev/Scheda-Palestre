import { useNavigate } from 'react-router';
import { Alert } from '../shared/components/Alert';
import { BrandMark } from '../app/layouts/BrandMark';
import { Button } from '../shared/components/Button';
import { ChangePasswordForm } from './ChangePasswordForm';
import { homePathFor, useCurrentUser, useLogout } from './useAuth';

/** First access / temporary password: the change is mandatory before anything else. */
export function ChangePasswordPage() {
  const navigate = useNavigate();
  const { data: user } = useCurrentUser();
  const logout = useLogout();

  return (
    <main className="auth-page">
      <div className="card auth-card">
        <div className="auth-card__brand">
          <BrandMark />
          GymPlanner
        </div>
        <h1>Cambia password</h1>
        {user?.mustChangePassword ? (
          <Alert tone="info" title="Cambio obbligatorio">
            <p>Stai usando una password temporanea. Scegline una nuova per continuare.</p>
          </Alert>
        ) : null}
        <div style={{ marginTop: 'var(--space-4)' }}>
          <ChangePasswordForm onChanged={(updated) => navigate(homePathFor(updated), { replace: true })} />
        </div>
        <Button
          variant="ghost"
          block
          style={{ marginTop: 'var(--space-3)' }}
          loading={logout.isPending}
          onClick={() => logout.mutate(undefined, { onSettled: () => navigate('/login', { replace: true }) })}
        >
          Esci
        </Button>
      </div>
    </main>
  );
}

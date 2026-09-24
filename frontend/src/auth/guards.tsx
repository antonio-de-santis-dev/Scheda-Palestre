import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import type { UserRole } from './api';
import { homePathFor, useCurrentUser } from './useAuth';
import { ErrorAlert } from '../shared/components/Alert';
import { LoadingState } from '../shared/components/States';
import { AccessDeniedPage } from '../app/router/AccessDeniedPage';

interface RequireAuthProps {
  role?: UserRole;
  children: ReactNode;
  /** When true the page is reachable while a password change is pending. */
  allowPasswordChange?: boolean;
}

/**
 * Client-side routing guard. It only improves navigation: every authorisation decision is
 * enforced again by the backend.
 */
export function RequireAuth({ role, children, allowPasswordChange = false }: RequireAuthProps) {
  const { data: user, isLoading, error, refetch } = useCurrentUser();
  const location = useLocation();

  if (isLoading) {
    return <LoadingState label="Verifica della sessione…" />;
  }
  if (error) {
    return (
      <main className="main main--narrow">
        <ErrorAlert error={error} onRetry={() => void refetch()} />
      </main>
    );
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  if (user.mustChangePassword && !allowPasswordChange) {
    return <Navigate to="/change-password" replace />;
  }
  if (role && user.role !== role) {
    if (user.role === 'ADMIN') {
      // An ADMIN opening the ordinary USER area goes to the admin dashboard.
      return <Navigate to="/admin" replace />;
    }
    return <AccessDeniedPage />;
  }
  return <>{children}</>;
}

/** Redirects to the right home according to the session. */
export function HomeRedirect() {
  const { data: user, isLoading } = useCurrentUser();
  if (isLoading) {
    return <LoadingState />;
  }
  return <Navigate to={user ? homePathFor(user) : '/login'} replace />;
}

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Navigate, useLocation, useNavigate } from 'react-router';
import { LogIn } from 'lucide-react';
import { Button } from '../shared/components/Button';
import { PasswordField, TextField } from '../shared/components/Field';
import { ErrorAlert } from '../shared/components/Alert';
import { LoadingState } from '../shared/components/States';
import { BrandMark } from '../app/layouts/BrandMark';
import { homePathFor, useCurrentUser, useLogin } from './useAuth';

const schema = z.object({
  username: z.string().trim().min(1, 'Inserisci lo username'),
  password: z.string().min(1, 'Inserisci la password'),
});

type LoginForm = z.infer<typeof schema>;

export function LoginPage() {
  const { data: user, isLoading } = useCurrentUser();
  const login = useLogin();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from;

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginForm>({ resolver: zodResolver(schema), defaultValues: { username: '', password: '' } });

  if (isLoading) {
    return <LoadingState />;
  }
  if (user && !login.isPending) {
    return <Navigate to={homePathFor(user)} replace />;
  }

  const onSubmit = handleSubmit((values) => {
    login.mutate(values, {
      onSuccess: (loggedIn) => {
        const home = homePathFor(loggedIn);
        const target = !loggedIn.mustChangePassword && from && from.startsWith(home) ? from : home;
        navigate(target, { replace: true });
      },
    });
  });

  return (
    <main className="auth-page">
      <div className="card auth-card">
        <div className="auth-card__brand">
          <BrandMark />
          GymPlanner
        </div>
        <h1>Accedi</h1>
        <p className="muted">Usa le credenziali ricevute dalla palestra.</p>
        <form className="form" onSubmit={onSubmit} noValidate>
          {login.error ? <ErrorAlert error={login.error} /> : null}
          <TextField
            label="Username"
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            required
            error={errors.username?.message}
            {...register('username')}
          />
          <PasswordField
            label="Password"
            autoComplete="current-password"
            required
            error={errors.password?.message}
            {...register('password')}
          />
          <Button type="submit" size="lg" block loading={login.isPending} icon={<LogIn size={22} aria-hidden="true" />}>
            Accedi
          </Button>
        </form>
      </div>
    </main>
  );
}

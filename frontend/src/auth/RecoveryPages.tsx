import { useState } from 'react';
import { Link, useLocation } from 'react-router';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { http } from '../shared/api/http';
import { Button } from '../shared/components/Button';
import { TextField, PasswordField } from '../shared/components/Field';
import { ErrorAlert } from '../shared/components/Alert';
import { isApiError } from '../shared/errors/ApiError';

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const request = useMutation({ mutationFn: () => http.post('/api/auth/forgot-password', { email: email.trim() }) });
  return <main className="auth-page"><section className="auth-card">
    <h1>Recupera password</h1>
    <p>Inserisci l’email associata al tuo account.</p>
    {request.isSuccess ? <p role="status">Se l’indirizzo corrisponde a un account attivo, riceverai un link per scegliere una nuova password. Controlla anche lo spam.</p> : <form className="form" onSubmit={(e) => { e.preventDefault(); request.mutate(); }}>
      {request.error ? <ErrorAlert error={request.error} /> : null}
      <TextField label="Email" error={isApiError(request.error) && request.error.fieldErrors.some((e) => e.field === 'email') ? 'Inserisci un indirizzo email valido.' : undefined} type="email" autoComplete="email" required maxLength={255} value={email} onChange={(e) => setEmail(e.target.value)} />
      <Button type="submit" loading={request.isPending}>Invia link di recupero</Button>
    </form>}
    <Link to="/login">Torna al login</Link>
  </section></main>;
}

export function ResetPasswordPage() {
  const location = useLocation();
  // Fragment is not sent to the web server, proxy access logs or referrer headers.
  const [token] = useState(() => new URLSearchParams(location.hash.slice(1)).get('token') ?? '');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const client = useQueryClient();
  const reset = useMutation({ mutationFn: () => http.post('/api/auth/reset-password', { token, newPassword: password }),
    onSuccess: () => { setPassword(''); setConfirm(''); client.clear(); window.history.replaceState(null, '', '/reset-password'); } });
  return <main className="auth-page"><section className="auth-card">
    <h1>Scegli una nuova password</h1>
    {reset.isSuccess ? <p role="status">Password aggiornata. Accedi con la nuova password; le altre sessioni sono state chiuse.</p> : !/^[A-Za-z0-9_-]{43}$/.test(token) ? <p role="alert">Link non valido. Richiedi un nuovo recupero.</p> : <form className="form" onSubmit={(e) => {
      e.preventDefault();
      if (password.length < 8 || password.length > 128 || !/\p{L}/u.test(password) || !/[0-9]/.test(password)) { setError('Usa da 8 a 128 caratteri, con lettere e cifre.'); return; }
      if (password !== confirm) { setError('Le password non coincidono.'); return; }
      setError(''); reset.mutate();
    }}>
      {error ? <p role="alert">{error}</p> : null}
      {reset.error ? isApiError(reset.error) && reset.error.code === 'RESET_LINK_INVALID' ? <p role="alert">Link scaduto o già utilizzato. Richiedi un nuovo recupero.</p> : <ErrorAlert error={reset.error} /> : null}
      <PasswordField label="Nuova password" autoComplete="new-password" required maxLength={128} value={password} onChange={(e) => setPassword(e.target.value)} />
      <PasswordField label="Conferma nuova password" autoComplete="new-password" required maxLength={128} value={confirm} onChange={(e) => setConfirm(e.target.value)} />
      <Button type="submit" loading={reset.isPending}>Salva nuova password</Button>
    </form>}
    <p><Link to="/forgot-password">Richiedi un nuovo link</Link> · <Link to="/login">Torna al login</Link></p>
  </section></main>;
}

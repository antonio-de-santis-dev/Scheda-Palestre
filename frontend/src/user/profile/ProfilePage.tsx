import { useState } from 'react';
import { Link } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Save } from 'lucide-react';
import { http } from '../../shared/api/http';
import { PageHeader } from '../../shared/components/PageHeader';
import { Button } from '../../shared/components/Button';
import { Alert, ErrorAlert } from '../../shared/components/Alert';
import { TextField } from '../../shared/components/Field';
import { QueryState } from '../../shared/components/States';
import { applyServerErrors } from '../../shared/errors/formErrors';
import { ChangePasswordForm } from '../../auth/ChangePasswordForm';
import type { UserRole } from '../../auth/api';

interface Profile {
  id: string;
  firstName: string;
  lastName: string;
  username: string;
  email: string;
  phone: string | null;
  role: UserRole;
}

const schema = z.object({
  phone: z
    .string()
    .trim()
    .max(30, 'Al massimo 30 caratteri')
    .refine((v) => v === '' || /^[+0-9 ()./-]{5,30}$/.test(v), 'Numero di telefono non valido'),
});

/** US-03: phone and password; the other data are read only. Shared by USER and ADMIN. */
export function ProfilePage() {
  const query = useQuery({ queryKey: ['me', 'profile'], queryFn: () => http.get<Profile>('/api/me/profile') });
  const [passwordChanged, setPasswordChanged] = useState(false);
  const p = query.data;
  return (
    <>
      <PageHeader title="Profilo" />
      <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
        {p ? (
          <div className="stack">
            <section className="card" aria-labelledby="profile-data">
              <h2 id="profile-data" className="card__title">
                I tuoi dati
              </h2>
              <dl className="form-grid form-grid--2" style={{ margin: 0 }}>
                {[
                  ['Nome', `${p.firstName} ${p.lastName}`],
                  ['Username', p.username],
                  ['Email', p.email],
                  ['Ruolo', p.role],
                ].map(([label, value]) => (
                  <div key={label}>
                    <dt className="muted small">{label}</dt>
                    <dd style={{ margin: 0, fontWeight: 600, overflowWrap: 'anywhere' }}>{value}</dd>
                  </div>
                ))}
              </dl>
              <p className="muted small" style={{ marginTop: 'var(--space-3)' }}>
                Per modificare questi dati rivolgiti alla palestra.
              </p>
              <PhoneForm key={p.id} initial={p.phone ?? ''} />
            </section>
            {p.role === 'USER' ? (
              <p>
                <Link to="/app/schedule">Modifica i giorni di allenamento</Link>
              </p>
            ) : null}
            <section className="card" aria-labelledby="password-title">
              <h2 id="password-title" className="card__title">
                Cambia password
              </h2>
              {passwordChanged ? (
                <div style={{ marginBottom: 'var(--space-3)' }}>
                  <Alert tone="success">
                    <p>Password aggiornata. Le altre sessioni aperte sono state chiuse.</p>
                  </Alert>
                </div>
              ) : null}
              <ChangePasswordForm onChanged={() => setPasswordChanged(true)} />
            </section>
          </div>
        ) : null}
      </QueryState>
    </>
  );
}

function PhoneForm({ initial }: { initial: string }) {
  const queryClient = useQueryClient();
  const [saved, setSaved] = useState(false);
  const save = useMutation({
    mutationFn: (phone: string | null) => http.put<Profile>('/api/me/profile', { phone }),
    onSuccess: (profile) => queryClient.setQueryData(['me', 'profile'], profile),
  });
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<{ phone: string }>({ resolver: zodResolver(schema), defaultValues: { phone: initial } });

  return (
    <form
      className="form"
      style={{ marginTop: 'var(--space-4)' }}
      noValidate
      onSubmit={handleSubmit(async ({ phone }) => {
        setSaved(false);
        try {
          await save.mutateAsync(phone === '' ? null : phone);
          setSaved(true);
        } catch (err) {
          applyServerErrors(err, setError, ['phone']);
        }
      })}
    >
      {saved ? (
        <Alert tone="success">
          <p>Telefono salvato.</p>
        </Alert>
      ) : null}
      {save.error && !errors.phone ? <ErrorAlert error={save.error} /> : null}
      <TextField label="Telefono" type="tel" hint="Facoltativo" error={errors.phone?.message} {...register('phone')} />
      <div className="form-actions">
        <Button type="submit" loading={save.isPending} icon={<Save size={18} aria-hidden="true" />}>
          Salva telefono
        </Button>
      </div>
    </form>
  );
}

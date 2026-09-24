import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Save } from 'lucide-react';
import { Button } from '../../shared/components/Button';
import { TextField } from '../../shared/components/Field';
import { ErrorAlert } from '../../shared/components/Alert';
import { applyServerErrors } from '../../shared/errors/formErrors';
import { isApiError } from '../../shared/errors/ApiError';
import type { UserInput } from './api';

/** Same rules as the backend (AdminUserDtos). */
const schema = z.object({
  firstName: z.string().trim().min(1, 'Inserisci il nome').max(80, 'Al massimo 80 caratteri'),
  lastName: z.string().trim().min(1, 'Inserisci il cognome').max(80, 'Al massimo 80 caratteri'),
  username: z
    .string()
    .trim()
    .min(3, 'Almeno 3 caratteri')
    .max(50, 'Al massimo 50 caratteri')
    .regex(/^[A-Za-z0-9._-]+$/, 'Solo lettere, cifre, punto, trattino e underscore'),
  email: z.string().trim().min(1, "Inserisci l'email").max(254).email('Email non valida'),
  phone: z
    .string()
    .trim()
    .max(30, 'Al massimo 30 caratteri')
    .refine((v) => v === '' || /^[+0-9 ()./-]{5,30}$/.test(v), 'Numero di telefono non valido'),
});

type FormValues = z.infer<typeof schema>;
const FIELDS = ['firstName', 'lastName', 'username', 'email', 'phone'] as const;

interface UserFormProps {
  initial?: UserInput;
  submitLabel: string;
  pending: boolean;
  error: unknown;
  onSubmit: (input: UserInput) => Promise<unknown>;
  onCancel?: () => void;
}

export function UserForm({ initial, submitLabel, pending, error, onSubmit, onCancel }: UserFormProps) {
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      firstName: initial?.firstName ?? '',
      lastName: initial?.lastName ?? '',
      username: initial?.username ?? '',
      email: initial?.email ?? '',
      phone: initial?.phone ?? '',
    },
  });

  const submit = handleSubmit(async (values) => {
    try {
      await onSubmit({ ...values, phone: values.phone === '' ? null : values.phone });
    } catch (err) {
      applyServerErrors(err, setError, FIELDS);
    }
  });

  const generalError = error && !(isApiError(error) && error.fieldErrors.length > 0) ? error : null;

  return (
    <form className="form" onSubmit={submit} noValidate>
      {generalError ? <ErrorAlert error={generalError} /> : null}
      <div className="form-grid form-grid--2">
        <TextField label="Nome" required autoComplete="off" error={errors.firstName?.message} {...register('firstName')} />
        <TextField label="Cognome" required autoComplete="off" error={errors.lastName?.message} {...register('lastName')} />
        <TextField
          label="Username"
          required
          autoComplete="off"
          autoCapitalize="none"
          spellCheck={false}
          error={errors.username?.message}
          {...register('username')}
        />
        <TextField label="Email" type="email" required autoComplete="off" error={errors.email?.message} {...register('email')} />
        <TextField label="Telefono" type="tel" hint="Facoltativo" autoComplete="off" error={errors.phone?.message} {...register('phone')} />
      </div>
      <div className="form-actions">
        {onCancel ? (
          <Button variant="secondary" onClick={onCancel} disabled={pending}>
            Annulla
          </Button>
        ) : null}
        <Button type="submit" loading={pending} icon={<Save size={18} aria-hidden="true" />}>
          {submitLabel}
        </Button>
      </div>
    </form>
  );
}

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { KeyRound } from 'lucide-react';
import { Button } from '../shared/components/Button';
import { PasswordField } from '../shared/components/Field';
import { ErrorAlert } from '../shared/components/Alert';
import { applyServerErrors } from '../shared/errors/formErrors';
import { isApiError } from '../shared/errors/ApiError';
import { changePasswordSchema, type ChangePasswordForm as FormValues } from './passwordSchema';
import { useChangePassword } from './useAuth';
import type { CurrentUser } from './api';

interface ChangePasswordFormProps {
  onChanged: (user: CurrentUser) => void;
}

export function ChangePasswordForm({ onChanged }: ChangePasswordFormProps) {
  const change = useChangePassword();
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  });

  const onSubmit = handleSubmit(({ currentPassword, newPassword }) => {
    change.mutate(
      { currentPassword, newPassword },
      {
        onSuccess: (user) => {
          reset();
          onChanged(user);
        },
        onError: (error) => applyServerErrors(error, setError, ['currentPassword', 'newPassword']),
      },
    );
  });

  // Field errors are shown next to the fields; anything else is shown at the top.
  const unhandled = change.error && !(isApiError(change.error) && change.error.fieldErrors.length > 0) ? change.error : null;

  return (
    <form className="form" onSubmit={onSubmit} noValidate>
      {unhandled ? <ErrorAlert error={unhandled} /> : null}
      <PasswordField
        label="Password attuale"
        autoComplete="current-password"
        required
        error={errors.currentPassword?.message}
        {...register('currentPassword')}
      />
      <PasswordField
        label="Nuova password"
        autoComplete="new-password"
        hint="Almeno 8 caratteri, con lettere e cifre."
        required
        error={errors.newPassword?.message}
        {...register('newPassword')}
      />
      <PasswordField
        label="Conferma nuova password"
        autoComplete="new-password"
        required
        error={errors.confirmPassword?.message}
        {...register('confirmPassword')}
      />
      <Button type="submit" block loading={change.isPending} icon={<KeyRound size={20} aria-hidden="true" />}>
        Cambia password
      </Button>
    </form>
  );
}

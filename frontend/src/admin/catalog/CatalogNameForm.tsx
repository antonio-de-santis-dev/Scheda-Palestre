import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Button } from '../../shared/components/Button';
import { TextField } from '../../shared/components/Field';
import { ErrorAlert } from '../../shared/components/Alert';
import { applyServerErrors } from '../../shared/errors/formErrors';
import { isApiError } from '../../shared/errors/ApiError';

const schema = z.object({
  name: z.string().trim().min(1, 'Inserisci un nome').max(100, 'Al massimo 100 caratteri'),
});

type FormValues = z.infer<typeof schema>;

interface CatalogNameFormProps {
  label: string;
  initialName?: string;
  submitLabel: string;
  pending: boolean;
  error: unknown;
  onSubmit: (name: string) => Promise<unknown>;
  onCancel?: () => void;
  resetOnSuccess?: boolean;
}

export function CatalogNameForm({
  label,
  initialName = '',
  submitLabel,
  pending,
  error,
  onSubmit,
  onCancel,
  resetOnSuccess = false,
}: CatalogNameFormProps) {
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { name: initialName } });

  const submit = handleSubmit(async ({ name }) => {
    try {
      await onSubmit(name);
      if (resetOnSuccess) {
        reset({ name: '' });
      }
    } catch (err) {
      applyServerErrors(err, setError, ['name']);
    }
  });

  const generalError = error && !(isApiError(error) && error.fieldErrors.length > 0) ? error : null;

  return (
    <form className="form" onSubmit={submit} noValidate>
      {generalError ? <ErrorAlert error={generalError} /> : null}
      <div className="toolbar" style={{ marginBottom: 0 }}>
        <TextField label={label} required autoComplete="off" error={errors.name?.message} {...register('name')} />
        <div className="row" style={{ paddingBottom: errors.name ? 'var(--space-6)' : 0 }}>
          {onCancel ? (
            <Button variant="secondary" onClick={onCancel} disabled={pending}>
              Annulla
            </Button>
          ) : null}
          <Button type="submit" loading={pending}>
            {submitLabel}
          </Button>
        </div>
      </div>
    </form>
  );
}

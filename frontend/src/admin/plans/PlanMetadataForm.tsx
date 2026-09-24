import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Save } from 'lucide-react';
import { Button } from '../../shared/components/Button';
import { TextAreaField, TextField } from '../../shared/components/Field';
import { ErrorAlert } from '../../shared/components/Alert';
import { applyServerErrors } from '../../shared/errors/formErrors';
import { isApiError } from '../../shared/errors/ApiError';
import type { PlanMetadata } from './api';

const schema = z.object({
  name: z.string().trim().min(1, 'Inserisci il nome della scheda').max(100, 'Al massimo 100 caratteri'),
  description: z.string().max(2000, 'Al massimo 2000 caratteri'),
  expiresOn: z.string(),
});

type FormValues = z.infer<typeof schema>;

interface PlanMetadataFormProps {
  initial?: PlanMetadata;
  submitLabel: string;
  pending: boolean;
  error: unknown;
  disabled?: boolean;
  onSubmit: (values: PlanMetadata) => Promise<unknown>;
  onCancel?: () => void;
}

export function PlanMetadataForm({ initial, submitLabel, pending, error, disabled, onSubmit, onCancel }: PlanMetadataFormProps) {
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: initial?.name ?? '',
      description: initial?.description ?? '',
      expiresOn: initial?.expiresOn ?? '',
    },
  });

  const submit = handleSubmit(async (values) => {
    try {
      await onSubmit({
        name: values.name.trim(),
        description: values.description.trim() === '' ? null : values.description.trim(),
        expiresOn: values.expiresOn === '' ? null : values.expiresOn,
      });
    } catch (err) {
      applyServerErrors(err, setError, ['name', 'description', 'expiresOn']);
    }
  });

  const generalError = error && !(isApiError(error) && error.fieldErrors.length > 0) ? error : null;

  return (
    <form className="form" onSubmit={submit} noValidate>
      {generalError ? <ErrorAlert error={generalError} /> : null}
      <fieldset disabled={disabled} style={{ border: 'none', padding: 0, margin: 0 }} className="form">
        <div className="form-grid form-grid--2">
          <TextField label="Nome scheda" required error={errors.name?.message} {...register('name')} />
          <TextField
            label="Scadenza"
            type="date"
            hint="Facoltativa, solo informativa"
            error={errors.expiresOn?.message}
            {...register('expiresOn')}
          />
        </div>
        <TextAreaField label="Descrizione" hint="Facoltativa" error={errors.description?.message} {...register('description')} />
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
      </fieldset>
    </form>
  );
}

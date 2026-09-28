import { useEffect, useState } from 'react';
import { Controller, useFieldArray, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Save } from 'lucide-react';
import { Button } from '../../shared/components/Button';
import { Checkbox, TextField } from '../../shared/components/Field';
import { Combobox, type ComboboxOption } from '../../shared/components/Combobox';
import { ErrorAlert } from '../../shared/components/Alert';
import { ConfirmDialog } from '../../shared/components/ConfirmDialog';
import { useActiveExercisesOfGroup } from '../catalog/api';
import type { PlanExercise } from '../../shared/api/planTypes';
import type { PlanExerciseInput } from './api';

const setSchema = z
  .object({
    reps: z.number({ message: 'Numero' }).int().min(0).max(100),
    toFailure: z.boolean(),
    restSeconds: z.number({ message: 'Numero' }).int().min(0, 'Min 0').max(600, 'Max 600'),
  })
  .refine((s) => s.toFailure || (s.reps >= 1 && s.reps <= 100), { path: ['reps'], message: '1-100' });

/** Same rules as the backend (spec 8.8/8.9). */
const schema = z
  .object({
    exerciseId: z.string().min(1, "Scegli l'esercizio"),
    setsCount: z.number({ message: 'Inserisci un numero' }).int().min(1, 'Almeno 1 serie').max(20, 'Al massimo 20 serie'),
    reps: z.number({ message: 'Inserisci un numero' }).int().min(0).max(100),
    toFailure: z.boolean(),
    restSeconds: z
      .number({ message: 'Inserisci un numero' })
      .int()
      .min(0, 'Minimo 0 secondi')
      .max(600, 'Massimo 600 secondi'),
    custom: z.boolean(),
    customSets: z.array(setSchema),
  })
  .refine((v) => v.toFailure || (v.reps >= 1 && v.reps <= 100), {
    path: ['reps'],
    message: 'Le ripetizioni devono essere tra 1 e 100',
  });

type FormValues = z.infer<typeof schema>;

interface ExerciseEditorProps {
  initial?: PlanExercise;
  /** Only exercises of the section's group can be chosen (ADR 0007). */
  muscleGroupId: string;
  muscleGroupName: string;
  pending: boolean;
  error: unknown;
  onSubmit: (input: PlanExerciseInput) => Promise<unknown>;
  onCancel: () => void;
}

export function ExerciseEditor({ initial, muscleGroupId, muscleGroupName, pending, error, onSubmit, onCancel }: ExerciseEditorProps) {
  const exercises = useActiveExercisesOfGroup(muscleGroupId);
  const [pendingValues, setPendingValues] = useState<PlanExerciseInput | null>(null);

  const {
    register,
    control,
    handleSubmit,
    watch,
    getValues,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      exerciseId: initial?.exerciseId ?? '',
      setsCount: initial?.setsCount ?? 3,
      reps: initial?.toFailure ? 0 : (initial?.reps ?? 10),
      toFailure: initial?.toFailure ?? false,
      restSeconds: initial?.restSeconds ?? 90,
      custom: initial?.customized ?? false,
      customSets: initial?.customized
        ? initial.sets.map((s) => ({ reps: s.reps, toFailure: s.toFailure, restSeconds: s.restSeconds }))
        : [],
    },
  });
  const { fields, append, remove } = useFieldArray({ control, name: 'customSets' });

  const setsCount = watch('setsCount');
  const custom = watch('custom');
  const toFailure = watch('toFailure');

  // Keep one row per set while customisation is on (rows added with the general values).
  useEffect(() => {
    if (!custom) {
      return;
    }
    const target = Number.isInteger(setsCount) ? Math.min(Math.max(setsCount, 1), 20) : fields.length;
    if (fields.length < target) {
      const { reps, toFailure: tf, restSeconds } = getValues();
      for (let i = fields.length; i < target; i++) {
        append({ reps: tf ? 0 : reps, toFailure: tf, restSeconds }, { shouldFocus: false });
      }
    } else if (fields.length > target) {
      remove(Array.from({ length: fields.length - target }, (_, i) => target + i));
    }
  }, [custom, setsCount, fields.length, append, remove, getValues]);

  // The exercise currently configured stays selectable even when deactivated or moved to another
  // group (historic data, spec 8.4 / ADR 0007); new choices come from the active exercises of the group.
  const options: ComboboxOption[] = (exercises.data?.content ?? []).map((o) => ({ id: o.id, label: o.name }));
  if (initial && !options.some((o) => o.id === initial.exerciseId)) {
    options.unshift({
      id: initial.exerciseId,
      label: initial.exerciseName,
      note: !initial.exerciseActive ? 'disattivato' : 'ora in un altro gruppo',
    });
  }

  const toInput = (values: FormValues): PlanExerciseInput => ({
    exerciseId: values.exerciseId,
    setsCount: values.setsCount,
    reps: values.toFailure ? 0 : values.reps,
    toFailure: values.toFailure,
    restSeconds: values.restSeconds,
    customSets: values.custom
      ? values.customSets.map((s, i) => ({
          setIndex: i + 1,
          reps: s.toFailure ? 0 : s.reps,
          toFailure: s.toFailure,
          restSeconds: s.restSeconds,
        }))
      : [],
  });

  const submit = handleSubmit(async (values) => {
    const input = toInput(values);
    // US-14: reducing the number of sets requires an explicit confirmation.
    if (initial && input.setsCount < initial.setsCount) {
      setPendingValues(input);
      return;
    }
    await onSubmit(input).catch(() => undefined);
  });

  return (
    <div className="editor-panel">
      <form className="form" onSubmit={submit} noValidate aria-label={initial ? 'Modifica esercizio' : 'Nuovo esercizio'}>
        {error ? <ErrorAlert error={error} /> : null}
        <Controller
          control={control}
          name="exerciseId"
          render={({ field }) => (
            <Combobox
              label="Esercizio"
              required
              placeholder={`Cerca tra gli esercizi di ${muscleGroupName}…`}
              options={options}
              value={field.value || null}
              onChange={(id) => field.onChange(id ?? '')}
              emptyText="Nessun esercizio trovato in questo gruppo"
              hint={`Solo esercizi attivi del gruppo ${muscleGroupName}.`}
              error={errors.exerciseId?.message}
            />
          )}
        />
        <div className="form-grid form-grid--2">
          <TextField
            label="Serie"
            type="number"
            inputMode="numeric"
            min={1}
            max={20}
            required
            error={errors.setsCount?.message}
            {...register('setsCount', { valueAsNumber: true })}
          />
          <TextField
            label="Recupero (secondi)"
            type="number"
            inputMode="numeric"
            min={0}
            max={600}
            hint="0 = nessun timer"
            required
            error={errors.restSeconds?.message}
            {...register('restSeconds', { valueAsNumber: true })}
          />
          <TextField
            label="Ripetizioni"
            type="number"
            inputMode="numeric"
            min={1}
            max={100}
            disabled={toFailure}
            hint={toFailure ? 'A cedimento: verrà mostrato MAX' : undefined}
            error={errors.reps?.message}
            {...register('reps', { valueAsNumber: true })}
          />
          <Checkbox label="A cedimento (MAX)" {...register('toFailure')} />
        </div>
        <Checkbox label="Personalizza ogni serie" {...register('custom')} />

        {custom ? (
          <div className="table-scroll">
            <table className="sets-table">
              <caption className="visually-hidden">Valori per serie</caption>
              <thead>
                <tr>
                  <th scope="col">Serie</th>
                  <th scope="col">Ripetizioni</th>
                  <th scope="col">MAX</th>
                  <th scope="col">Recupero (s)</th>
                </tr>
              </thead>
              <tbody>
                {fields.map((field, index) => {
                  const rowFailure = watch(`customSets.${index}.toFailure`);
                  const rowErrors = errors.customSets?.[index];
                  return (
                    <tr key={field.id}>
                      <th scope="row">{index + 1}</th>
                      <td>
                        <input
                          className="input"
                          type="number"
                          inputMode="numeric"
                          aria-label={`Ripetizioni serie ${index + 1}`}
                          aria-invalid={rowErrors?.reps ? true : undefined}
                          disabled={rowFailure}
                          {...register(`customSets.${index}.reps`, { valueAsNumber: true })}
                        />
                      </td>
                      <td>
                        <input
                          type="checkbox"
                          style={{ width: 24, height: 24 }}
                          aria-label={`Serie ${index + 1} a cedimento`}
                          {...register(`customSets.${index}.toFailure`)}
                        />
                      </td>
                      <td>
                        <input
                          className="input"
                          type="number"
                          inputMode="numeric"
                          aria-label={`Recupero serie ${index + 1} in secondi`}
                          aria-invalid={rowErrors?.restSeconds ? true : undefined}
                          {...register(`customSets.${index}.restSeconds`, { valueAsNumber: true })}
                        />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
            {errors.customSets ? (
              <p className="field__error" role="alert">
                Controlla i valori delle serie: ripetizioni 1-100 (o MAX), recupero 0-600 secondi.
              </p>
            ) : null}
          </div>
        ) : null}

        <div className="form-actions">
          <Button variant="secondary" onClick={onCancel} disabled={pending}>
            Annulla
          </Button>
          <Button type="submit" loading={pending} icon={<Save size={18} aria-hidden="true" />}>
            {initial ? 'Salva esercizio' : 'Aggiungi esercizio'}
          </Button>
        </div>
      </form>
      <ConfirmDialog
        open={pendingValues !== null}
        title="Ridurre il numero di serie?"
        confirmLabel="Riduci serie"
        tone="primary"
        loading={pending}
        onCancel={() => setPendingValues(null)}
        onConfirm={() => {
          if (pendingValues) {
            void onSubmit(pendingValues)
              .catch(() => undefined)
              .finally(() => setPendingValues(null));
          }
        }}
      >
        <p>
          Le serie passeranno da {initial?.setsCount} a {pendingValues?.setsCount}. Le serie in eccesso e le relative
          personalizzazioni verranno eliminate.
        </p>
      </ConfirmDialog>
    </div>
  );
}

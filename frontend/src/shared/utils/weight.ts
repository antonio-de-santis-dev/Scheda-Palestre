import { z } from 'zod';

/** Accept Italian comma or decimal point; empty is unrecorded, distinct from zero. */
export const optionalWeightSchema = z.string().trim()
  .refine((v) => v === '' || /^(?:\d+(?:[.,]\d{1,2})?|[.,]\d{1,2})$/.test(v), 'Inserisci kg con massimo 2 decimali')
  .refine((v) => v === '' || (Number(v.replace(',', '.')) >= 0 && Number(v.replace(',', '.')) <= 1000), 'Peso tra 0 e 1000 kg')
  .transform((v) => v === '' ? null : Number(v.replace(',', '.')));

export const setResultSchema = z.object({
  weightKgUsed: optionalWeightSchema,
  repsActual: z.string().trim()
    .refine((v) => v === '' || /^\d+$/.test(v), 'Inserisci un numero intero di ripetizioni')
    .refine((v) => v === '' || Number(v) <= 1000, 'Ripetizioni tra 0 e 1000')
    .transform((v) => v === '' ? null : Number(v)),
});

const weightFormat = new Intl.NumberFormat('it-IT', { maximumFractionDigits: 2 });
export function formatWeight(weight: number | null | undefined): string {
  return weight == null ? 'Non indicato' : `${weightFormat.format(weight)} kg`;
}

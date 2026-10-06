import { useState } from 'react';
import { Button } from '../../shared/components/Button';
import { TextField } from '../../shared/components/Field';
import type { SetResults, WorkoutSetState } from './api';

/** Results belong to the completed recovery series, never to the next current series. */
export function RecoveryResultsForm({ set, exerciseName, busy, loading, onSave }: {
  set: WorkoutSetState; exerciseName: string; busy: boolean; loading: boolean; onSave: (results: SetResults) => void;
}) {
  const [weight, setWeight] = useState(set.weightKgUsed == null ? '' : String(set.weightKgUsed));
  const [reps, setReps] = useState(set.repsActual == null ? '' : String(set.repsActual));
  const [errors, setErrors] = useState<{ weight?: string; reps?: string }>({});
  return <form className="stack card" aria-label="Risultati della serie appena svolta" noValidate onSubmit={(event) => {
    event.preventDefault(); if (busy) return;
    const kg = weight.trim().replace(',', '.'); const repetitions = reps.trim();
    const invalid = {
      weight: kg && (!/^\d+(?:\.\d{1,2})?$/.test(kg) || Number(kg) > 1000)
        ? 'Inserisci un peso da 0 a 1000 kg, con al massimo due decimali.' : undefined,
      reps: repetitions && (!/^\d+$/.test(repetitions) || Number(repetitions) > 1000)
        ? 'Inserisci un numero intero di ripetizioni da 0 a 1000.' : undefined,
    };
    setErrors(invalid); if (invalid.weight || invalid.reps) return;
    onSave({ weightKgUsed: kg === '' ? null : Number(kg), repsActual: repetitions === '' ? null : Number(repetitions) });
  }}>
    <h3 style={{ margin: 0 }}>Serie appena svolta: {exerciseName} · {set.setIndex}</h3>
    <div className="form-grid form-grid--2">
      <TextField label="Peso usato (kg)" inputMode="decimal" value={weight} disabled={busy}
        error={errors.weight} onChange={(event) => setWeight(event.target.value)} />
      <TextField label="Ripetizioni effettive" inputMode="numeric" value={reps} disabled={busy}
        error={errors.reps} onChange={(event) => setReps(event.target.value)} />
    </div>
    <p className="small muted" style={{ margin: 0 }}>Facoltativi: i campi vuoti restano non registrati. Salva prima della fine del recupero.</p>
    <Button type="submit" disabled={busy} loading={loading} block>Salva risultati</Button>
  </form>;
}

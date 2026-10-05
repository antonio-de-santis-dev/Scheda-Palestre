import { useState } from 'react';
import { CheckCheck, Hourglass } from 'lucide-react';
import { Button } from '../../shared/components/Button';
import { TextField } from '../../shared/components/Field';
import { formatDuration } from '../../shared/utils/format';
import type { SetResults } from './api';

/** Mounted with the current set's key: results are never carried to another set. */
export function CompleteSetForm({ busy, loading, resting, paused, remaining, onComplete }: {
  busy: boolean; loading: boolean; resting: boolean; paused: boolean; remaining: number;
  onComplete: (results: SetResults) => void;
}) {
  const [weight, setWeight] = useState('');
  const [reps, setReps] = useState('');
  const [errors, setErrors] = useState<{ weight?: string; reps?: string }>({});
  return (
    <form className="stack" noValidate onSubmit={(event) => {
      event.preventDefault();
      if (busy || resting) return;
      const kg = weight.trim().replace(',', '.');
      const repetitions = reps.trim();
      const invalid = {
        weight: kg && (!/^\d+(?:\.\d{1,2})?$/.test(kg) || Number(kg) > 1000)
          ? 'Inserisci un peso da 0 a 1000 kg, con al massimo due decimali.' : undefined,
        reps: repetitions && (!/^\d+$/.test(repetitions) || Number(repetitions) > 1000)
          ? 'Inserisci un numero intero di ripetizioni da 0 a 1000.' : undefined,
      };
      setErrors(invalid);
      if (invalid.weight || invalid.reps) return;
      onComplete({ weightKgUsed: kg === '' ? null : Number(kg), repsActual: repetitions === '' ? null : Number(repetitions) });
    }}>
      <div className="form-grid form-grid--2">
        <TextField label="Peso usato (kg)" inputMode="decimal" value={weight} disabled={busy}
          error={errors.weight} onChange={(event) => setWeight(event.target.value)} />
        <TextField label="Ripetizioni effettive" inputMode="numeric" value={reps} disabled={busy}
          error={errors.reps} onChange={(event) => setReps(event.target.value)} />
      </div>
      <p className="small muted" style={{ margin: 0 }}>Facoltativi: i campi vuoti restano non registrati nello storico.</p>
      <Button type="submit" size="lg" block className={resting ? 'btn--waiting' : undefined}
        aria-disabled={resting || undefined} aria-describedby={resting ? 'rest-hint' : undefined}
        icon={resting ? <Hourglass size={26} aria-hidden="true" /> : <CheckCheck size={26} aria-hidden="true" />}
        loading={loading} disabled={busy}>
        Fine serie
        {resting ? <span className="btn__sub" aria-hidden="true">{paused ? 'recupero in pausa' : `tra ${formatDuration(remaining)}`}</span> : null}
      </Button>
      {resting ? <p id="rest-hint" className="small muted center" style={{ margin: 0 }}>
        Disponibile al termine del recupero. Puoi comunque saltare l'esercizio o interrompere l'allenamento.
      </p> : null}
    </form>
  );
}

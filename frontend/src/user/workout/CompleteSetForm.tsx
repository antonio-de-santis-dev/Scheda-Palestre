import { CheckCheck, Hourglass } from 'lucide-react';
import { Button } from '../../shared/components/Button';
import { formatDuration } from '../../shared/utils/format';

/** Mounted with the current set's key: results are never carried to another set. */
export function CompleteSetForm({ busy, loading, resting, paused, remaining, onComplete }: {
  busy: boolean; loading: boolean; resting: boolean; paused: boolean; remaining: number;
  onComplete: () => void;
}) {
  return (
    <form className="stack" onSubmit={(event) => {
      event.preventDefault();
      if (!busy && !resting) onComplete();
    }}>
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

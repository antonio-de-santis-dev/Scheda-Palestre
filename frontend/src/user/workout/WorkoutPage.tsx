import { useRef, useState, type CSSProperties } from 'react';
import { Link, useParams } from 'react-router';
import { CheckCheck, Hourglass, OctagonX, PartyPopper, SkipForward, TimerReset, Trophy, Volume2, VolumeX } from 'lucide-react';
import { Button } from '../../shared/components/Button';
import { Alert, ErrorAlert } from '../../shared/components/Alert';
import { ConfirmDialog } from '../../shared/components/ConfirmDialog';
import { QueryState } from '../../shared/components/States';
import { isApiError } from '../../shared/errors/ApiError';
import { formatDuration, formatRest } from '../../shared/utils/format';
import { repsLabel } from '../../shared/api/planTypes';
import { STALE_STATE_CODES, useWorkout, useWorkoutAction, workoutApi, type WorkoutState } from './api';
import { useRestTimer } from './useRestTimer';
import { ExerciseStatusBadge, WorkoutStatusBadge } from './ExerciseStatusBadge';
import { celebrate, transitions, type Feedback } from './feedback';
import { useRestAlert } from './useRestAlert';
import { WorkoutDuration } from '../../shared/components/WorkoutDuration';
import type { RestAction, SetResultInput } from './api';
import { TextField } from '../../shared/components/Field';
import { formatWeight, setResultSchema } from '../../shared/utils/weight';

/** Workout execution (US-17, US-18, US-20, US-23): usable one-handed from 360 px. */
export function WorkoutPage() {
  const { id = '' } = useParams();
  const query = useWorkout(id);
  return (
    <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()} loadingLabel="Caricamento allenamento…">
      {query.data ? <WorkoutView state={query.data} refetch={() => void query.refetch()} /> : null}
    </QueryState>
  );
}

function WorkoutView({ state, refetch }: { state: WorkoutState; refetch: () => void }) {
  const [confirm, setConfirm] = useState<'skip' | 'interrupt' | 'restSkip' | null>(null);
  const [announcement, setAnnouncement] = useState('');
  const [draft, setDraft] = useState({ setId: '', weightKgUsed: '', repsActual: '' });
  const [resultErrors, setResultErrors] = useState<{ setId: string; weightKgUsed?: string; repsActual?: string }>({ setId: '' });
  const [feedback, setFeedback] = useState<Feedback | null>(null);
  // Transitions already celebrated: refetches, retries and StrictMode never repeat them.
  const celebrated = useRef(new Set<string>());
  const restAlert = useRestAlert();
  const complete = useWorkoutAction(state.workoutId, ({ setId, result }: { setId: string; result: SetResultInput }) => workoutApi.completeSet(state.workoutId, setId, result), true);
  const skip = useWorkoutAction(state.workoutId, (exerciseId: string) => workoutApi.skip(state.workoutId, exerciseId), true);
  const interrupt = useWorkoutAction(state.workoutId, () => workoutApi.interrupt(state.workoutId), true);

  const rest = useWorkoutAction(state.workoutId,
    ({ action, seconds }: { action: RestAction; seconds?: number }) =>
      workoutApi.changeRest(state.workoutId, action, state.restVersion, seconds), false);
  const busy = complete.isPending || skip.isPending || interrupt.isPending || rest.isPending;

  const remaining = useRestTimer(state, refetch, () => {
    setAnnouncement('Recupero terminato: puoi completare la prossima serie.');
    restAlert.play();
    if ('vibrate' in navigator) {
      navigator.vibrate?.(300);
    }
  });
  const resting = remaining > 0;

  /** Called only from a successful mutation (never from an effect watching the data). */
  const onActionSuccess = (before: WorkoutState, after: WorkoutState) => {
    const fresh = transitions(before, after).filter((t) => !celebrated.current.has(t.key));
    fresh.forEach((t) => celebrated.current.add(t.key));
    if (fresh.length === 0) {
      return;
    }
    const main = fresh.find((t) => t.kind === 'workout') ?? fresh.find((t) => t.kind === 'group') ?? fresh[0]!;
    const message = fresh.map((t) => t.message).join(' ');
    setFeedback({ ...main, message });
    void celebrate(main.kind);
  };

  const error = complete.error ?? skip.error ?? interrupt.error ?? rest.error;
  // Stale screens (e.g. set completed from another tab) are re-synced by the mutation hook.
  const stale = isApiError(error) && STALE_STATE_CODES.includes(error.code);

  const current = state.exercises.find((e) => e.id === state.currentExerciseId) ?? null;
  const currentSet = current?.sets.find((s) => s.id === state.currentSetId) ?? null;
  const values = draft.setId === currentSet?.id ? draft : { setId: currentSet?.id ?? '', weightKgUsed: '', repsActual: '' };
  const validation: { weightKgUsed?: string; repsActual?: string } = resultErrors.setId === currentSet?.id ? resultErrors : {};
  const finished = state.status !== 'IN_PROGRESS';
  const done = state.exercises.filter((e) => e.status === 'COMPLETED' || e.status === 'SKIPPED').length;
  const totalSets = state.exercises.reduce((n, e) => n + e.setsPlanned, 0);
  const completedSets = state.exercises.reduce((n, e) => n + e.setsCompleted, 0);
  const progress = totalSets ? Math.round(completedSets / totalSets * 100) : 0;

  return (
    <div className="workout">
      <header className="row row--between workout-header">
        <div>
          <h1 style={{ fontSize: 'var(--text-2xl)', margin: 0 }}>{state.sessionTitle}</h1>
          <p className="muted small" style={{ margin: 0 }}>
            {state.planName} · esercizi {done}/{state.exercises.length}
          </p>
        </div>
        <div className="row">
          <label className="row small">
            <input type="checkbox" checked={restAlert.enabled} onChange={(e) => restAlert.setEnabled(e.target.checked)} />
            {restAlert.enabled ? <Volume2 size={18} aria-hidden="true" /> : <VolumeX size={18} aria-hidden="true" />}
            Suono fine recupero
          </label>
          {restAlert.enabled ? (
            <label className="row small">
              Suono
              <select className="input" aria-label="Suono di fine recupero" value={restAlert.sound}
                onChange={(e) => restAlert.setSound(e.target.value as 'alert-1' | 'alert-2' | 'alert-3')}>
                <option value="alert-1">Suono 1</option>
                <option value="alert-2">Suono 2</option>
                <option value="alert-3">Suono 3</option>
              </select>
            </label>
          ) : null}
          <WorkoutStatusBadge status={state.status} />
        </div>
      </header>

      <WorkoutDuration state={state} />
      <p className="small muted">Include recuperi e tempo trascorso fuori dalla pagina.</p>

      {/* Announced once when the rest ends; the countdown itself is never a live region. */}
      <div className="visually-hidden" role="status">
        {announcement}
      </div>
      <div role="status" aria-live="polite">
        {feedback ? (
          <div className={`alert alert--success celebration celebration--${feedback.kind}`}>
            <PartyPopper size={22} aria-hidden="true" />
            <div className="alert__body">
              <p>{feedback.message}</p>
            </div>
          </div>
        ) : null}
      </div>
      {stale ? (
        <Alert tone="info">
          <p>
            {isApiError(error) && error.code === 'REST_NOT_FINISHED'
              ? 'Il recupero non è ancora finito: la schermata è stata allineata al server.'
              : 'La schermata è stata aggiornata con lo stato più recente.'}
          </p>
        </Alert>
      ) : error ? (
        <ErrorAlert error={error} />
      ) : null}

      <div className="workout-progress" role="progressbar" aria-label="Serie completate" aria-valuenow={progress} aria-valuemin={0} aria-valuemax={100}>
        <span style={{ width: `${progress}%` }} />
      </div>
      <div className="workout-layout">
        <div className="workout-primary">
      {finished ? (
        <FinishedCard state={state} />
      ) : current && currentSet ? (
        <section className="workout-current" aria-labelledby="current-exercise">
          <div className="workout-current__section">{current.muscleGroupName}</div>
          <h2 id="current-exercise" className="workout-current__name">
            {current.exerciseName}
          </h2>
          <div className="set-progress" aria-label={`Serie ${currentSet.setIndex} di ${current.setsPlanned}`}>
            {current.sets.map((set) => <span key={set.id} className={set.completedAt ? 'set-progress--done' : set.id === currentSet.id ? 'set-progress--current' : ''} />)}
          </div>
          <div className="workout-current__stats">
            <div className="stat">
              <span className="stat__label">Serie</span>
              <span className="stat__value">
                {currentSet.setIndex}/{current.setsPlanned}
              </span>
            </div>
            <div className="stat">
              <span className="stat__label">Ripetizioni</span>
              <span className="stat__value">{repsLabel({ reps: currentSet.repsPlanned, toFailure: currentSet.toFailure })}</span>
            </div>
            <div className="stat">
              <span className="stat__label">Recupero</span>
              <span className="stat__value" style={{ fontSize: 'var(--text-lg)' }}>
                {formatRest(currentSet.restSeconds)}
              </span>
            </div>
          </div>

          {remaining > 0 ? (
            <div className="timer timer--ring" style={{ '--rest-progress': `${Math.min(100, remaining / Math.max(1, state.restSeconds ?? currentSet.restSeconds) * 100)}%` } as CSSProperties} role="timer" aria-live="off" aria-label={`Recupero: ${formatDuration(remaining)} rimanenti`}>
              <span className="timer__label">
                <Hourglass size={22} aria-hidden="true" />
                {state.restPaused ? 'Recupero in pausa' : 'Recupero'}
              </span>
              <span className="timer__value">{formatDuration(remaining)}</span>
            </div>
          ) : announcement ? (
            <div className="timer timer--done" role="status">
              <span className="timer__label">
                <TimerReset size={22} aria-hidden="true" />
                Recupero terminato
              </span>
            </div>
          ) : null}

          {resting ? (
            <div className="rest-controls" role="group" aria-label="Controlli recupero">
              <Button variant="secondary" disabled={busy} onClick={() => rest.mutate({ action: state.restPaused ? 'RESUME' : 'PAUSE' })}>
                {state.restPaused ? 'Riprendi recupero' : 'Pausa recupero'}
              </Button>
              <Button variant="secondary" disabled={busy} onClick={() => rest.mutate({ action: 'EXTEND', seconds: 30 })}>+30 secondi</Button>
              <Button variant="ghost" disabled={busy} onClick={() => setConfirm('restSkip')}>Salta recupero</Button>
            </div>
          ) : null}

          <fieldset className="set-results" disabled={busy}>
            <legend>Dati della serie (facoltativi)</legend>
            <p className="small muted">Peso previsto: {formatWeight(currentSet.weightKgPlanned)}. I campi vuoti restano non registrati.</p>
            <div className="form-grid form-grid--2">
              <TextField label="Peso usato (kg)" inputMode="decimal" placeholder="Non registrato" value={values.weightKgUsed}
                error={validation.weightKgUsed} onChange={(e) => setDraft({ ...values, weightKgUsed: e.target.value })} />
              <TextField label="Ripetizioni effettive" inputMode="numeric" placeholder="Non registrate" value={values.repsActual}
                error={validation.repsActual} onChange={(e) => setDraft({ ...values, repsActual: e.target.value })} />
            </div>
          </fieldset>

          {/* aria-disabled (not disabled): the button stays in the tab order and says why it waits. */}
          <Button
            size="lg"
            block
            className={resting ? 'btn--waiting' : undefined}
            aria-disabled={resting || busy || undefined}
            aria-describedby={resting ? 'rest-hint' : undefined}
            icon={resting ? <Hourglass size={26} aria-hidden="true" /> : <CheckCheck size={26} aria-hidden="true" />}
            loading={complete.isPending}
            onClick={() => {
              if (resting || busy) {
                return;
              }
              const parsed = setResultSchema.safeParse(values);
              if (!parsed.success) {
                const fields = parsed.error.flatten().fieldErrors;
                setResultErrors({ setId: currentSet.id, weightKgUsed: fields.weightKgUsed?.[0], repsActual: fields.repsActual?.[0] });
                return;
              }
              setResultErrors({ setId: currentSet.id });
              setAnnouncement('');
              setFeedback(null);
              skip.reset();
              rest.reset();
              const before = state;
              complete.mutate({ setId: currentSet.id, result: parsed.data }, { onSuccess: (after) => onActionSuccess(before, after) });
            }}
          >
            Fine serie
            {resting ? <span className="btn__sub" aria-hidden="true">{state.restPaused ? 'recupero in pausa' : `tra ${formatDuration(remaining)}`}</span> : null}
          </Button>
          {resting ? (
            <p id="rest-hint" className="small muted center" style={{ margin: 'var(--space-2) 0 0' }}>
              Disponibile al termine del recupero. Puoi comunque saltare l'esercizio o interrompere l'allenamento.
            </p>
          ) : null}
        </section>
      ) : (
        <Alert tone="info">
          <p>Sincronizzazione in corso…</p>
        </Alert>
      )}

      {!finished && current ? (
        <div className="workout-actions">
          <Button variant="secondary" icon={<SkipForward size={20} aria-hidden="true" />} disabled={busy} onClick={() => setConfirm('skip')}>
            Salta esercizio
          </Button>
          <Button variant="secondary" icon={<OctagonX size={20} aria-hidden="true" />} disabled={busy} onClick={() => setConfirm('interrupt')}>
            Interrompi
          </Button>
        </div>
      ) : null}

      </div>
      <section className="card workout-path" aria-labelledby="exercise-list-title">
        <h2 id="exercise-list-title" style={{ fontSize: 'var(--text-xl)' }}>
          Esercizi
        </h2>
        <ol className="list workout-exercises">
          {state.exercises.map((e) => (
            <li key={e.id} className={`list-item${e.id === state.currentExerciseId ? ' list-item--current' : ''}${e.status === 'COMPLETED' ? ' list-item--done' : ''}`}>
              <div className="list-item__main">
                <div className="list-item__title">
                  <span className="exercise-row__number" aria-hidden="true">{e.position}</span>{e.exerciseName}
                </div>
                <div className="list-item__meta">
                  {e.muscleGroupName} · {e.setsCompleted}/{e.setsPlanned} serie ·{' '}
                  {e.sets.map((s) => repsLabel({ reps: s.repsPlanned, toFailure: s.toFailure })).join(' / ')}
                </div>
              </div>
              <ExerciseStatusBadge status={e.status} />
            </li>
          ))}
        </ol>
      </section>

      </div>

      <ConfirmDialog open={confirm === 'restSkip'} title="Saltare il recupero?" confirmLabel="Salta recupero" tone="primary"
        loading={rest.isPending} onCancel={() => setConfirm(null)}
        onConfirm={() => rest.mutate({ action: 'SKIP' }, { onSettled: () => setConfirm(null) })}>
        <p>Il recupero verrà terminato e potrai completare la prossima serie.</p>
      </ConfirmDialog>
      <ConfirmDialog
        open={confirm === 'skip'}
        title={`Saltare “${current?.exerciseName ?? ''}”?`}
        confirmLabel="Salta esercizio"
        tone="primary"
        loading={skip.isPending}
        onCancel={() => setConfirm(null)}
        onConfirm={() => {
          if (current) {
            const before = state;
            skip.mutate(current.id, {
              onSuccess: (after) => onActionSuccess(before, after),
              onSettled: () => setConfirm(null),
            });
          }
        }}
      >
        <p>Le serie già completate restano salvate. L'esercizio saltato non potrà essere ripreso in questo allenamento.</p>
      </ConfirmDialog>
      <ConfirmDialog
        open={confirm === 'interrupt'}
        title="Interrompere l'allenamento?"
        confirmLabel="Interrompi"
        loading={interrupt.isPending}
        onCancel={() => setConfirm(null)}
        onConfirm={() => interrupt.mutate(undefined, { onSettled: () => setConfirm(null) })}
      >
        <p>L'allenamento verrà chiuso con le serie svolte finora e non potrà essere ripreso.</p>
      </ConfirmDialog>
    </div>
  );
}

function FinishedCard({ state }: { state: WorkoutState }) {
  const completed = state.exercises.filter((e) => e.status === 'COMPLETED').length;
  const skipped = state.exercises.filter((e) => e.status === 'SKIPPED').length;
  return (
    <section className="card center" aria-labelledby="finished-title">
      <span className="state__icon" aria-hidden="true">
        <Trophy size={44} />
      </span>
      <h2 id="finished-title">{state.status === 'COMPLETED' ? 'Allenamento completato!' : 'Allenamento interrotto'}</h2>
      <p>
        {completed} esercizi completati{skipped ? `, ${skipped} saltati` : ''} su {state.exercises.length}.
      </p>
      <Link to="/app/today" className="btn btn--primary">
        Torna a Oggi
      </Link>
    </section>
  );
}

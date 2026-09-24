import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { CheckCheck, Hourglass, OctagonX, SkipForward, TimerReset, Trophy } from 'lucide-react';
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

/** Guided execution (US-17, US-18, US-20, US-23): usable one-handed from 360 px. */
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
  const [confirm, setConfirm] = useState<'skip' | 'interrupt' | null>(null);
  const [announcement, setAnnouncement] = useState('');
  const complete = useWorkoutAction(state.workoutId, (setId: string) => workoutApi.completeSet(state.workoutId, setId), true);
  const skip = useWorkoutAction(state.workoutId, (exerciseId: string) => workoutApi.skip(state.workoutId, exerciseId), true);
  const interrupt = useWorkoutAction(state.workoutId, () => workoutApi.interrupt(state.workoutId), true);

  const remaining = useRestTimer(state, refetch, () => {
    setAnnouncement('Recupero terminato');
    if ('vibrate' in navigator) {
      navigator.vibrate?.(300);
    }
  });

  const error = complete.error ?? skip.error ?? interrupt.error;
  // Stale screens (e.g. set completed from another tab) are re-synced by the mutation hook.
  const stale = isApiError(error) && STALE_STATE_CODES.includes(error.code);

  const current = state.exercises.find((e) => e.id === state.currentExerciseId) ?? null;
  const currentSet = current?.sets.find((s) => s.id === state.currentSetId) ?? null;
  const finished = state.status !== 'IN_PROGRESS';
  const done = state.exercises.filter((e) => e.status === 'COMPLETED' || e.status === 'SKIPPED').length;

  return (
    <div className="workout">
      <header className="row row--between">
        <div>
          <h1 style={{ fontSize: 'var(--text-2xl)', margin: 0 }}>{state.sessionTitle}</h1>
          <p className="muted small" style={{ margin: 0 }}>
            {state.planName} · esercizi {done}/{state.exercises.length}
          </p>
        </div>
        <WorkoutStatusBadge status={state.status} />
      </header>

      <div className="visually-hidden" aria-live="assertive">
        {announcement}
      </div>
      {stale ? (
        <Alert tone="info">
          <p>La schermata è stata aggiornata con lo stato più recente.</p>
        </Alert>
      ) : error ? (
        <ErrorAlert error={error} />
      ) : null}

      {finished ? (
        <FinishedCard state={state} />
      ) : current && currentSet ? (
        <section className="workout-current" aria-labelledby="current-exercise">
          <div className="workout-current__section">{current.muscleGroupName}</div>
          <h2 id="current-exercise" className="workout-current__name">
            {current.exerciseName}
          </h2>
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
            <div className="timer" role="timer" aria-live="off" aria-label={`Recupero: ${formatDuration(remaining)} rimanenti`}>
              <span className="timer__label">
                <Hourglass size={22} aria-hidden="true" />
                Recupero
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

          <Button
            size="lg"
            block
            icon={<CheckCheck size={26} aria-hidden="true" />}
            loading={complete.isPending}
            onClick={() => {
              setAnnouncement('');
              skip.reset();
              complete.mutate(currentSet.id);
            }}
          >
            Fine serie
          </Button>
          {remaining > 0 ? (
            <p className="small muted center" style={{ margin: 'var(--space-2) 0 0' }}>
              Il timer è indicativo: puoi iniziare la serie quando sei pronto.
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
          <Button variant="secondary" icon={<SkipForward size={20} aria-hidden="true" />} onClick={() => setConfirm('skip')}>
            Salta esercizio
          </Button>
          <Button variant="secondary" icon={<OctagonX size={20} aria-hidden="true" />} onClick={() => setConfirm('interrupt')}>
            Interrompi
          </Button>
        </div>
      ) : null}

      <section aria-labelledby="exercise-list-title">
        <h2 id="exercise-list-title" style={{ fontSize: 'var(--text-xl)' }}>
          Esercizi
        </h2>
        <ol className="list workout-exercises">
          {state.exercises.map((e) => (
            <li key={e.id} className={`list-item${e.id === state.currentExerciseId ? ' list-item--current' : ''}`}>
              <div className="list-item__main">
                <div className="list-item__title">
                  {e.position}. {e.exerciseName}
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

      <ConfirmDialog
        open={confirm === 'skip'}
        title={`Saltare “${current?.exerciseName ?? ''}”?`}
        confirmLabel="Salta esercizio"
        tone="primary"
        loading={skip.isPending}
        onCancel={() => setConfirm(null)}
        onConfirm={() => {
          if (current) {
            skip.mutate(current.id, { onSettled: () => setConfirm(null) });
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

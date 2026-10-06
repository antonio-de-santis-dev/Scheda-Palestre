import { useRef, useState, type CSSProperties } from 'react';
import { Link, useParams } from 'react-router';
import { Hourglass, Pause, Play, Plus, OctagonX, PartyPopper, SkipForward, TimerReset, Trophy, Volume2, VolumeX } from 'lucide-react';
import { Button } from '../../shared/components/Button';
import { Alert, ErrorAlert } from '../../shared/components/Alert';
import { ConfirmDialog } from '../../shared/components/ConfirmDialog';
import { QueryState } from '../../shared/components/States';
import { isApiError } from '../../shared/errors/ApiError';
import { formatDuration, formatRest } from '../../shared/utils/format';
import { repsLabel } from '../../shared/api/planTypes';
import { STALE_STATE_CODES, useWorkout, useWorkoutAction, workoutApi, type RecordSetResultsRequest, type RestAction, type RestRequest, type WorkoutState } from './api';
import { useRestTimer } from './useRestTimer';
import { WorkoutStatusBadge } from './ExerciseStatusBadge';
import { celebrate, transitions, type Feedback } from './feedback';
import { useRestAlert } from './useRestAlert';
import { WorkoutDuration } from './WorkoutDuration';
import { WorkoutExerciseList } from './WorkoutExerciseList';
import { RecoveryResultsForm } from './RecoveryResultsForm';
import { CompleteSetForm } from './CompleteSetForm';

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
  const [confirm, setConfirm] = useState<'skip' | 'interrupt' | null>(null);
  const [restConfirm, setRestConfirm] = useState<RestRequest | null>(null);
  const [announcement, setAnnouncement] = useState('');
  const [feedback, setFeedback] = useState<Feedback | null>(null);
  // Transitions already celebrated: refetches, retries and StrictMode never repeat them.
  const celebrated = useRef(new Set<string>());
  const restAlert = useRestAlert();
  const complete = useWorkoutAction(state.workoutId, (setId: string) => workoutApi.completeSet(state.workoutId, setId, { weightKgUsed: null, repsActual: null }), true);
  const results = useWorkoutAction(state.workoutId,
    (args: { setId: string; request: RecordSetResultsRequest }) => workoutApi.recordResults(state.workoutId, args.setId, args.request), true);
  const skip = useWorkoutAction(state.workoutId, (exerciseId: string) => workoutApi.skip(state.workoutId, exerciseId), true);
  const interrupt = useWorkoutAction(state.workoutId, () => workoutApi.interrupt(state.workoutId), true);

  const reorder = useWorkoutAction(state.workoutId,
    (args: { exerciseIds: string[]; expectedVersion: number }) =>
      workoutApi.reorder(state.workoutId, args.exerciseIds, args.expectedVersion), true);
  const recovery = useWorkoutAction(state.workoutId,
    (request: RestRequest) => workoutApi.changeRest(state.workoutId, request), true);
  const recoveryRequest = (action: RestAction): RestRequest => ({ action,
    expectedVersion: state.restVersion, expectedExecutionVersion: state.executionVersion });
  const changeRecovery = (request: RestRequest) => {
    if (busy) return;
    complete.reset(); skip.reset(); interrupt.reset(); reorder.reset(); recovery.reset();
    setAnnouncement('');
    recovery.mutate(request, {
      onSuccess: () => setAnnouncement(request.action === 'SKIP' ? 'Recupero saltato: puoi completare la prossima serie.'
        : request.action === 'PAUSE' ? 'Recupero in pausa.' : request.action === 'RESUME' ? 'Recupero ripreso.' : 'Aggiunti 30 secondi al recupero.'),
      onSettled: () => setRestConfirm(null),
    });
  };
  const [dragging, setDragging] = useState(false);
  const saving = results.isPending || complete.isPending || skip.isPending || interrupt.isPending || reorder.isPending || recovery.isPending;
  const busy = saving || dragging;
  const saveOrder = (ids: string[]) => {
    if (saving) return;
    complete.reset(); skip.reset(); interrupt.reset(); reorder.reset(); recovery.reset();
    setFeedback(null);
    reorder.mutate({ exerciseIds: ids, expectedVersion: state.executionVersion }, {
      onSuccess: (after) => {
        const first = after.exercises.find((e) => e.id === after.currentExerciseId);
        setAnnouncement(`Ordine salvato. Esercizio corrente: ${first?.exerciseName ?? ''}.`);
      },
    });
  };

  const timerState = state.finalResultEndsAt ? { ...state, restEndsAt: state.finalResultEndsAt, restPaused: false } : state;
  const remaining = useRestTimer(timerState, refetch, () => {
    setAnnouncement(state.status === 'COMPLETED' ? 'Recupero finale terminato.' : 'Recupero terminato: puoi completare la prossima serie.');
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

  const error = results.error ?? recovery.error ?? reorder.error ?? complete.error ?? skip.error ?? interrupt.error;
  // Stale screens (e.g. set completed from another tab) are re-synced by the mutation hook.
  const stale = isApiError(error) && STALE_STATE_CODES.includes(error.code);

  const current = state.exercises.find((e) => e.id === state.currentExerciseId) ?? null;
  const currentSet = current?.sets.find((s) => s.id === state.currentSetId) ?? null;
  const finished = state.status !== 'IN_PROGRESS';
  const done = state.exercises.filter((e) => e.status === 'COMPLETED' || e.status === 'SKIPPED').length;
  const totalSets = state.exercises.reduce((n, e) => n + e.setsPlanned, 0);
  const completedSets = state.exercises.reduce((n, e) => n + e.setsCompleted, 0);
  const progress = totalSets ? Math.round(completedSets / totalSets * 100) : 0;

  const resultExercise = state.exercises.find((e) => e.sets.some((s) => s.id === state.resultEntrySetId));
  const resultSet = resultExercise?.sets.find((s) => s.id === state.resultEntrySetId);
  const recoveryResults = resting && resultExercise && resultSet ? <RecoveryResultsForm
    key={`${resultSet.id}:${resultSet.weightKgUsed}:${resultSet.repsActual}`} set={resultSet}
    exerciseName={resultExercise.exerciseName} busy={busy} loading={results.isPending}
    onSave={(values) => {
      results.mutate({ setId: resultSet.id, request: { results: values,
        expectedExecutionVersion: state.executionVersion, expectedRestVersion: state.restVersion } },
        { onSuccess: () => setAnnouncement('Risultati della serie salvati.') });
    }} /> : null;

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
        <>
          {resting && state.finalResultEndsAt ? <div className="stack">
            <div className="timer" role="timer" aria-label="Recupero finale" aria-live="off">
              <span className="timer__label">Recupero finale</span><span className="timer__value">{formatDuration(remaining)}</span>
            </div>
            {recoveryResults}
          </div> : null}
          <FinishedCard state={state} />
        </>
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
            <div className="timer timer--ring" style={{ '--rest-progress': `${Math.min(100, remaining / Math.max(1, state.restSeconds ?? currentSet.restSeconds) * 100)}%` } as CSSProperties} role="timer" aria-live="off" aria-label={`Recupero${state.restPaused ? ' in pausa' : ''}: ${formatDuration(remaining)} rimanenti`}>
              <span className="timer__label">
                <Hourglass size={22} aria-hidden="true" />
                {state.restPaused ? 'Recupero in pausa' : 'Recupero'}
              </span>
              <span className="timer__value">{formatDuration(remaining)}</span>
            </div>
          ) : announcement.startsWith('Recupero terminato') ? (
            <div className="timer timer--done" role="status">
              <span className="timer__label">
                <TimerReset size={22} aria-hidden="true" />
                Recupero terminato
              </span>
            </div>
          ) : null}

          {remaining > 0 ? (
            <div className="rest-controls" role="group" aria-label="Controlli recupero">
              <Button variant="secondary" size="sm" disabled={busy}
                icon={state.restPaused ? <Play size={18} aria-hidden="true" /> : <Pause size={18} aria-hidden="true" />}
                onClick={() => changeRecovery(recoveryRequest(state.restPaused ? 'RESUME' : 'PAUSE'))}>
                {state.restPaused ? 'Riprendi recupero' : 'Pausa recupero'}
              </Button>
              <Button variant="secondary" size="sm" icon={<Plus size={18} aria-hidden="true" />}
                disabled={busy} onClick={() => changeRecovery(recoveryRequest('EXTEND'))}>30 secondi</Button>
              <Button variant="secondary" size="sm" icon={<SkipForward size={18} aria-hidden="true" />}
                disabled={busy} onClick={() => setRestConfirm(recoveryRequest('SKIP'))}>Salta recupero</Button>
            </div>
          ) : null}

          {recoveryResults}
          <CompleteSetForm key={currentSet.id} busy={busy} loading={complete.isPending}
            resting={resting} paused={state.restPaused} remaining={remaining}
            onComplete={() => {
              setAnnouncement('');
              setFeedback(null);
              skip.reset(); recovery.reset(); reorder.reset(); interrupt.reset();
              const before = state;
              complete.mutate(currentSet.id, { onSuccess: (after) => onActionSuccess(before, after) });
            }} />
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
        <WorkoutExerciseList key={`${state.workoutId}:${state.executionVersion}:${state.status}`}
          state={state} busy={saving} onSave={saveOrder} onDraggingChange={setDragging} />
      </section>

      </div>

      <ConfirmDialog open={restConfirm !== null} title="Saltare il recupero?" confirmLabel="Salta recupero"
        tone="primary" loading={recovery.isPending} onCancel={() => setRestConfirm(null)}
        onConfirm={() => { if (restConfirm) changeRecovery(restConfirm); }}>
        <p>Il recupero terminerà subito e potrai completare la prossima serie. Le serie già svolte restano salvate.</p>
      </ConfirmDialog>

      <ConfirmDialog
        open={confirm === 'skip'}
        title={`Saltare “${current?.exerciseName ?? ''}”?`}
        confirmLabel="Salta esercizio"
        tone="primary"
        loading={skip.isPending}
        onCancel={() => setConfirm(null)}
        onConfirm={() => {
          if (current && !busy) {
            recovery.reset(); reorder.reset(); complete.reset(); interrupt.reset();
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
        onConfirm={() => {
          if (busy) return;
          recovery.reset(); reorder.reset(); complete.reset(); skip.reset();
          interrupt.mutate(undefined, { onSettled: () => setConfirm(null) });
        }}
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
      <p><WorkoutDuration status={state.status} seconds={state.durationSeconds} /></p>
      <Link to="/app/today" className="btn btn--primary">
        Torna a Oggi
      </Link>
    </section>
  );
}

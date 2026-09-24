import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { BedDouble, CalendarClock, ClipboardList, Play, RotateCcw } from 'lucide-react';
import { PageHeader } from '../../shared/components/PageHeader';
import { Button } from '../../shared/components/Button';
import { Alert, ErrorAlert } from '../../shared/components/Alert';
import { ConfirmDialog } from '../../shared/components/ConfirmDialog';
import { EmptyState, QueryState } from '../../shared/components/States';
import { PlanSessionView } from '../../shared/components/PlanSessionView';
import { formatDate, formatLongDate, todayIso } from '../../shared/utils/format';
import { useToday, useWorkoutAction, workoutApi, type Today } from '../workout/api';
import { WorkoutStatusBadge } from '../workout/ExerciseStatusBadge';

/** US-16: planned workout, rest day, plan in preparation or missing days. */
export function TodayPage() {
  const date = todayIso();
  const query = useToday(date);
  return (
    <>
      <PageHeader title="Oggi" subtitle={formatLongDate(date)} />
      <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
        {query.data ? <TodayContent today={query.data} /> : null}
      </QueryState>
    </>
  );
}

function TodayContent({ today }: { today: Today }) {
  const navigate = useNavigate();
  const start = useWorkoutAction('new', () => workoutApi.start(today.date), false);

  return (
    <div className="stack">
      {today.pendingWorkout ? <PendingWorkout today={today} /> : null}

      {today.status === 'NO_ACTIVE_ASSIGNMENT' ? (
        <EmptyState title="Nessuna scheda attiva" icon={<ClipboardList size={40} />}>
          <p>La palestra non ti ha ancora assegnato una scheda attiva.</p>
        </EmptyState>
      ) : null}

      {today.status === 'NO_SCHEDULE' ? (
        <EmptyState title="Scegli i tuoi giorni" icon={<CalendarClock size={40} />}>
          <p>Indica in quali giorni ti alleni: le sessioni di “{today.planName}” verranno distribuite automaticamente.</p>
          <Link to="/app/schedule" className="btn btn--primary">
            Scegli i giorni
          </Link>
        </EmptyState>
      ) : null}

      {today.status === 'PLAN_NOT_READY' ? (
        <EmptyState title="Scheda in preparazione" icon={<ClipboardList size={40} />}>
          <p>La scheda “{today.planName}” non è ancora pronta. Riprova più tardi.</p>
        </EmptyState>
      ) : null}

      {today.status === 'REST_DAY' || today.status === 'NOT_STARTED_YET' ? (
        <EmptyState
          title={today.status === 'REST_DAY' ? 'Giorno di riposo' : 'La scheda non è ancora iniziata'}
          icon={<BedDouble size={40} />}
        >
          {today.nextTraining ? (
            <p>
              Prossimo allenamento: <strong>{today.nextTraining.sessionTitle}</strong>, {formatLongDate(today.nextTraining.date)}.
            </p>
          ) : null}
          <Link to="/app/calendar" className="btn btn--secondary">
            Vedi calendario
          </Link>
        </EmptyState>
      ) : null}

      {today.status === 'TRAINING_DAY' && today.session ? (
        <>
          <section className="card" aria-labelledby="today-session">
            <div className="row row--between">
              <div>
                <p className="muted small" style={{ margin: 0 }}>
                  {today.planName}
                </p>
                <h2 id="today-session" style={{ margin: 0 }}>
                  {today.session.title}
                </h2>
              </div>
              {today.workout ? <WorkoutStatusBadge status={today.workout.status} /> : null}
            </div>
            <div style={{ marginTop: 'var(--space-4)' }}>
              {start.error ? <ErrorAlert error={start.error} /> : null}
              {today.canStart ? (
                <Button
                  size="lg"
                  block
                  icon={<Play size={24} aria-hidden="true" />}
                  loading={start.isPending}
                  onClick={() => start.mutate(undefined, { onSuccess: (state) => navigate(`/app/workout/${state.workoutId}`) })}
                >
                  Inizia allenamento
                </Button>
              ) : today.workout?.status === 'IN_PROGRESS' ? (
                <Link to={`/app/workout/${today.workout.id}`} className="btn btn--primary btn--lg btn--block">
                  <RotateCcw size={24} aria-hidden="true" />
                  Riprendi allenamento
                </Link>
              ) : today.workout ? (
                <Link to={`/app/history/${today.workout.id}`} className="btn btn--secondary btn--block">
                  Vedi il riepilogo
                </Link>
              ) : null}
            </div>
          </section>
          <PlanSessionView session={today.session} headingLevel={3} />
        </>
      ) : null}
    </div>
  );
}

/** O-04: a workout left in progress on another day requires an explicit choice. */
function PendingWorkout({ today }: { today: Today }) {
  const pending = today.pendingWorkout!;
  const [confirm, setConfirm] = useState(false);
  const interrupt = useWorkoutAction(pending.id, () => workoutApi.interrupt(pending.id), true);
  return (
    <>
      <Alert
        tone="warning"
        title="Allenamento non concluso"
        action={undefined}
      >
        <p>
          “{pending.sessionTitle}” del {formatDate(pending.scheduledDate)} è ancora in corso. Vuoi riprenderlo o interromperlo?
        </p>
        {interrupt.error ? <ErrorAlert error={interrupt.error} /> : null}
        <div className="row">
          <Link to={`/app/workout/${pending.id}`} className="btn btn--primary btn--sm">
            Riprendi
          </Link>
          <Button variant="secondary" size="sm" onClick={() => setConfirm(true)}>
            Interrompi
          </Button>
        </div>
      </Alert>
      <ConfirmDialog
        open={confirm}
        title="Interrompere l'allenamento non concluso?"
        confirmLabel="Interrompi"
        loading={interrupt.isPending}
        onCancel={() => setConfirm(false)}
        onConfirm={() => interrupt.mutate(undefined, { onSettled: () => setConfirm(false) })}
      >
        <p>Le serie svolte restano nello storico.</p>
      </ConfirmDialog>
    </>
  );
}

import { useSearchParams, useParams, Link } from 'react-router';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { History } from 'lucide-react';
import { http } from '../../shared/api/http';
import type { Page } from '../../shared/api/types';
import { PageHeader } from '../../shared/components/PageHeader';
import { EmptyState, QueryState } from '../../shared/components/States';
import { Pagination } from '../../shared/components/Pagination';
import { formatDateTime, formatLongDate, formatRest } from '../../shared/utils/format';
import { repsLabel } from '../../shared/api/planTypes';
import { useWorkout, type WorkoutSummary } from '../workout/api';
import { ExerciseStatusBadge, WorkoutStatusBadge } from '../workout/ExerciseStatusBadge';

function useHistory(page: number) {
  return useQuery({
    queryKey: ['me', 'history', page],
    queryFn: () => http.get<Page<WorkoutSummary>>('/api/me/workouts', { page, size: 20 }),
    placeholderData: keepPreviousData,
  });
}

/** US-24: essential history with snapshot values. */
export function HistoryPage() {
  const [params, setParams] = useSearchParams();
  const page = Number(params.get('page') ?? '0') || 0;
  const query = useHistory(page);
  return (
    <>
      <PageHeader title="Storico" subtitle="I tuoi allenamenti, dal più recente." />
      <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
        {query.data && query.data.content.length === 0 ? (
          <EmptyState title="Nessun allenamento ancora" icon={<History size={40} />}>
            <p>Gli allenamenti svolti compariranno qui.</p>
          </EmptyState>
        ) : (
          <>
            <ul className="list" aria-label="Allenamenti">
              {query.data?.content.map((w) => (
                <li key={w.id} className="list-item">
                  <div className="list-item__main">
                    <div className="list-item__title">
                      <Link to={`/app/history/${w.id}`}>
                        {w.sessionTitle} · {formatLongDate(w.scheduledDate)}
                      </Link>
                    </div>
                    <div className="list-item__meta">
                      {w.planName} · {w.completedExercises} completati
                      {w.skippedExercises ? `, ${w.skippedExercises} saltati` : ''} su {w.totalExercises}
                    </div>
                  </div>
                  <WorkoutStatusBadge status={w.status} />
                </li>
              ))}
            </ul>
            <Pagination
              page={query.data?.page ?? 0}
              totalPages={query.data?.totalPages ?? 0}
              onChange={(p) => setParams({ page: String(p) })}
            />
          </>
        )}
      </QueryState>
    </>
  );
}

export function HistoryDetailPage() {
  const { workoutId = '' } = useParams();
  const query = useWorkout(workoutId);
  const w = query.data;
  return (
    <>
      <PageHeader
        title={w ? w.sessionTitle : 'Allenamento'}
        subtitle={w ? `${w.planName} · ${formatLongDate(w.scheduledDate)}` : undefined}
        back={{ to: '/app/history', label: 'Storico' }}
      />
      <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
        {w ? (
          <div className="stack">
            <div className="row">
              <WorkoutStatusBadge status={w.status} />
              <span className="muted small">
                Iniziato {formatDateTime(w.startedAt)}
                {w.finishedAt ? ` · concluso ${formatDateTime(w.finishedAt)}` : ''}
              </span>
            </div>
            {w.status === 'IN_PROGRESS' ? (
              <Link to={`/app/workout/${w.workoutId}`} className="btn btn--primary">
                Riprendi allenamento
              </Link>
            ) : null}
            {w.exercises.map((e) => (
              <section key={e.id} className="card" aria-label={e.exerciseName}>
                <div className="row row--between">
                  <div>
                    <div className="workout-current__section">{e.muscleGroupName}</div>
                    <h2 style={{ fontSize: 'var(--text-xl)', margin: 0 }}>
                      {e.position}. {e.exerciseName}
                    </h2>
                  </div>
                  <ExerciseStatusBadge status={e.status} />
                </div>
                <div className="table-scroll">
                  <table className="sets-table">
                    <caption className="visually-hidden">Serie di {e.exerciseName}</caption>
                    <thead>
                      <tr>
                        <th scope="col">Serie</th>
                        <th scope="col">Ripetizioni</th>
                        <th scope="col">Recupero</th>
                        <th scope="col">Esito</th>
                      </tr>
                    </thead>
                    <tbody>
                      {e.sets.map((s) => (
                        <tr key={s.id}>
                          <th scope="row">{s.setIndex}</th>
                          <td>{repsLabel({ reps: s.repsPlanned, toFailure: s.toFailure })}</td>
                          <td>{formatRest(s.restSeconds)}</td>
                          <td>{s.completedAt ? '✓ Completata' : '— Non svolta'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>
            ))}
          </div>
        ) : null}
      </QueryState>
    </>
  );
}

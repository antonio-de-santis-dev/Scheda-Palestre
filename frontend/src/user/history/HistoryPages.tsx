import { useSearchParams, useParams, Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { History } from 'lucide-react';
import { http } from '../../shared/api/http';
import type { Page } from '../../shared/api/types';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { HistoryStats } from './HistoryStats';
import { HistoryFilterForm } from './HistoryFilterForm';
import { HISTORY_FILTER_KEYS, historyFilterError, historyPage, readHistoryFilters, type HistoryFilters } from './filters';
import { PageHeader } from '../../shared/components/PageHeader';
import { EmptyState, QueryState } from '../../shared/components/States';
import { Pagination } from '../../shared/components/Pagination';
import { formatDateTime, formatLongDate, formatRest } from '../../shared/utils/format';
import { repsLabel } from '../../shared/api/planTypes';
import { useWorkout, type WorkoutSummary } from '../workout/api';
import { WorkoutVolume } from './WorkoutVolume';
import { WorkoutDuration } from '../workout/WorkoutDuration';
import { ExerciseStatusBadge, WorkoutStatusBadge } from '../workout/ExerciseStatusBadge';

function useHistory(page: number, filters: HistoryFilters, enabled: boolean) {
  return useQuery({
    queryKey: ['me', 'history', page, filters],
    queryFn: () => http.get<Page<WorkoutSummary>>('/api/me/workouts', { page, size: 20, ...filters }),
    enabled,
  });
}

/** US-24: essential history with snapshot values. */
export function HistoryPage() {
  const [params, setParams] = useSearchParams();
  const page = historyPage(params);
  const filters = readHistoryFilters(params);
  const invalid = historyFilterError(filters);
  const query = useHistory(page, filters, !invalid);
  const filtered = HISTORY_FILTER_KEYS.some((key) => !!filters[key]);
  const changePage = (nextPage: number) => {
    const next = new URLSearchParams(params);
    if (nextPage) next.set('page', String(nextPage)); else next.delete('page');
    setParams(next);
  };
  const apply = (values: HistoryFilters) => {
    const next = new URLSearchParams(params);
    next.delete('page');
    for (const key of HISTORY_FILTER_KEYS) {
      if (values[key]) next.set(key, values[key]); else next.delete(key);
    }
    setParams(next);
  };
  const search = params.toString();
  return (
    <>
      <PageHeader title="Storico" subtitle="I tuoi allenamenti, dal più recente." />
      <HistoryFilterForm key={search} filters={filters} onApply={apply}
        onReset={() => apply({ from: '', to: '', status: '', q: '' })} />
      {!invalid ? <HistoryStats filters={filters} /> : null}
      {invalid ? <Alert tone="error"><p>{invalid}</p></Alert> : <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
        {query.data && query.data.content.length === 0 ? (
          <EmptyState title={query.data.totalElements > 0 ? 'Nessun allenamento in questa pagina'
            : filtered ? 'Nessun allenamento corrisponde ai filtri' : 'Nessun allenamento ancora'} icon={<History size={40} />}>
            <p>{filtered ? 'Modifica o azzera i filtri per cercare altri allenamenti.' : 'Gli allenamenti svolti compariranno qui.'}</p>
            {query.data.totalElements > 0 ? <Button variant="secondary" onClick={() => changePage(0)}>Vai alla prima pagina</Button> : null}
          </EmptyState>
        ) : (
          <>
            {query.data ? <p className="small muted" role="status">{query.data.totalElements} allenamenti</p> : null}
            <ul className="list" aria-label="Allenamenti">
              {query.data?.content.map((w) => (
                <li key={w.id} className="list-item">
                  <div className="list-item__main">
                    <div className="list-item__title">
                      <Link to={`/app/history/${w.id}${search ? `?${search}` : ''}`}>
                        {w.sessionTitle} · {formatLongDate(w.scheduledDate)}
                      </Link>
                    </div>
                    <div className="list-item__meta">
                      {w.planName} · {w.completedExercises} completati
                      {w.skippedExercises ? `, ${w.skippedExercises} saltati` : ''} su {w.totalExercises}
                    </div>
                    <div className="list-item__meta"><WorkoutDuration status={w.status} seconds={w.durationSeconds} /></div>
                    <div className="list-item__meta"><WorkoutVolume volume={w.volume} /></div>
                  </div>
                  <WorkoutStatusBadge status={w.status} />
                </li>
              ))}
            </ul>
            <Pagination
              page={query.data?.page ?? 0}
              totalPages={query.data?.totalPages ?? 0}
              onChange={changePage}
            />
          </>
        )}
      </QueryState>}
    </>
  );
}

export function HistoryDetailPage() {
  const { workoutId = '' } = useParams();
  const [params] = useSearchParams();
  const search = params.toString();
  const query = useWorkout(workoutId);
  const w = query.data;
  return (
    <>
      <PageHeader
        title={w ? w.sessionTitle : 'Allenamento'}
        subtitle={w ? `${w.planName} · ${formatLongDate(w.scheduledDate)}` : undefined}
        back={{ to: `/app/history${search ? `?${search}` : ''}`, label: 'Storico' }}
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
            <p className="small" style={{ margin: 0 }}><WorkoutDuration status={w.status} seconds={w.durationSeconds} /></p>
            <WorkoutVolume volume={w.volume} detailed />
            <p className="small muted" style={{ margin: 0 }}>Volume registrato = somma del peso usato × ripetizioni effettive delle serie completate. I dati mancanti non vengono stimati.</p>
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
                <WorkoutVolume volume={e.volume} detailed />
                {e.identity?.source === 'LEGACY' ? (
                  <p className="small muted">Identità storica limitata: questo esercizio sarà confrontabile solo con le registrazioni che conservano la stessa identità, senza associazioni basate sul nome.</p>
                ) : null}
                <div className="table-scroll">
                  <table className="sets-table">
                    <caption className="visually-hidden">Serie di {e.exerciseName}</caption>
                    <thead>
                      <tr>
                        <th scope="col">Serie</th>
                        <th scope="col">Ripetizioni previste</th>
                        <th scope="col">Ripetizioni effettive</th>
                        <th scope="col">Peso usato (kg)</th>
                        <th scope="col">Recupero</th>
                        <th scope="col">Esito</th>
                      </tr>
                    </thead>
                    <tbody>
                      {e.sets.map((s) => (
                        <tr key={s.id}>
                          <th scope="row">{s.setIndex}</th>
                          <td>{repsLabel({ reps: s.repsPlanned, toFailure: s.toFailure })}</td>
                          <td>{s.completedAt ? s.repsActual ?? 'Non registrate' : '—'}</td>
                          <td>{s.completedAt ? (s.weightKgUsed == null ? 'Non registrato' : s.weightKgUsed.toLocaleString('it-IT', { maximumFractionDigits: 2 })) : '—'}</td>
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

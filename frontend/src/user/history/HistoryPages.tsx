import { useSearchParams, useParams, Link } from 'react-router';
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { History } from 'lucide-react';
import { http } from '../../shared/api/http';
import type { Page } from '../../shared/api/types';
import { PageHeader } from '../../shared/components/PageHeader';
import { EmptyState, QueryState } from '../../shared/components/States';
import { Pagination } from '../../shared/components/Pagination';
import { formatDateTime, formatLongDate, formatRest, formatWorkoutDuration } from '../../shared/utils/format';
import { repsLabel } from '../../shared/api/planTypes';
import { useWorkout, type WorkoutSummary } from '../workout/api';
import { formatWeight } from '../../shared/utils/weight';
import { WorkoutDuration } from '../../shared/components/WorkoutDuration';
import { ExerciseStatusBadge, WorkoutStatusBadge } from '../workout/ExerciseStatusBadge';

function useHistory(page: number, filters: { from: string; to: string; status: string; search: string }) {
  return useQuery({
    queryKey: ['me', 'history', page, filters],
    queryFn: () => http.get<Page<WorkoutSummary>>('/api/me/workouts', { page, size: 20, ...filters }),
  });
}

/** US-24: essential history with snapshot values. */
export function HistoryPage() {
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Math.floor(Number(params.get('page') ?? '0') || 0));
  const filters = { from: params.get('from') ?? '', to: params.get('to') ?? '', status: params.get('status') ?? '', search: params.get('search') ?? '' };
  const query = useHistory(page, filters);
  const [filterError, setFilterError] = useState('');
  const filtered = Object.values(filters).some(Boolean);
  return (
    <>
      <PageHeader title="Storico" subtitle="I tuoi allenamenti, dal più recente."
        actions={<Link to="/app/progress" className="btn btn--primary">Statistiche e record</Link>} />
      <form key={JSON.stringify(filters)} className="card progress-filters" aria-label="Filtri storico" onSubmit={(event) => {
        event.preventDefault();
        const data = new FormData(event.currentTarget);
        const next = Object.fromEntries(['from', 'to', 'status', 'search'].map((key) => [key, String(data.get(key) ?? '').trim()]));
        if (next.from && next.to && next.from > next.to) { setFilterError('La data finale deve seguire quella iniziale.'); return; }
        setFilterError('');
        setParams(Object.fromEntries(Object.entries(next).filter(([, value]) => value !== '')));
      }}>
        <label className="field">Dal<input type="date" name="from" defaultValue={filters.from} /></label>
        <label className="field">Al<input type="date" name="to" defaultValue={filters.to} /></label>
        <label className="field">Esito<select name="status" defaultValue={filters.status}>
          <option value="">Tutti gli esiti</option><option value="COMPLETED">Completati</option>
          <option value="INTERRUPTED">Interrotti</option><option value="IN_PROGRESS">In corso</option>
        </select></label>
        <label className="field">Scheda o sessione<input type="search" name="search" maxLength={100} defaultValue={filters.search} placeholder="Cerca per nome" /></label>
        <button className="btn btn--primary" type="submit">Applica filtri</button>
        <button className="btn btn--secondary" type="button" onClick={() => { setFilterError(''); setParams({}); }}>Azzera filtri</button>
        {filterError ? <p role="alert">{filterError}</p> : null}
      </form>
      <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
        {query.data && query.data.content.length === 0 ? (
          <EmptyState title={filtered ? "Nessun risultato per questi filtri" : "Nessun allenamento ancora"} icon={<History size={40} />}>
            <p>{filtered ? "Prova a cambiare il periodo, il nome o l’esito." : "Gli allenamenti svolti compariranno qui."}</p>
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
                      {w.durationSeconds != null ? ` · Tempo impiegato: ${formatWorkoutDuration(w.durationSeconds)}` : ' · In corso'}
                    </div>
                  </div>
                  <WorkoutStatusBadge status={w.status} />
                </li>
              ))}
            </ul>
            <Pagination
              page={query.data?.page ?? 0}
              totalPages={query.data?.totalPages ?? 0}
              onChange={(p) => { const next = new URLSearchParams(params); next.set('page', String(p)); setParams(next); }}
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
            <WorkoutDuration state={w} />
            <p className="small muted">Include recuperi e tempo trascorso fuori dalla pagina.</p>
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
                        <th scope="col">Ripetizioni previste</th>
                        <th scope="col">Peso previsto</th>
                        <th scope="col">Peso usato</th>
                        <th scope="col">Ripetizioni effettive</th>
                        <th scope="col">Recupero</th>
                        <th scope="col">Esito</th>
                      </tr>
                    </thead>
                    <tbody>
                      {e.sets.map((s) => (
                        <tr key={s.id}>
                          <th scope="row">{s.setIndex}</th>
                          <td>{repsLabel({ reps: s.repsPlanned, toFailure: s.toFailure })}</td>
                          <td>{formatWeight(s.weightKgPlanned)}</td>
                          <td>{s.completedAt ? s.weightKgUsed == null ? 'Non registrato' : formatWeight(s.weightKgUsed) : '—'}</td>
                          <td>{s.completedAt ? s.repsActual ?? 'Non registrate' : '—'}</td>
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

import { useSearchParams, useParams, Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { History } from 'lucide-react';
import { http } from '../../shared/api/http';
import type { Page } from '../../shared/api/types';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { HistoryProgress } from './HistoryProgress';
import { HistoryStats } from './HistoryStats';
import { HistoryFilterForm } from './HistoryFilterForm';
import { HISTORY_FILTER_KEYS, historyFilterError, historyPage, readHistoryFilters, type HistoryFilters, type HistoryScope } from './filters';
import { StatusBadge } from '../../shared/components/StatusBadge';
import { PageHeader } from '../../shared/components/PageHeader';
import { EmptyState, QueryState } from '../../shared/components/States';
import { Pagination } from '../../shared/components/Pagination';
import { formatDateTime, formatLongDate, formatRest } from '../../shared/utils/format';
import { repsLabel } from '../../shared/api/planTypes';
import { useWorkout, type WorkoutSummary } from '../workout/api';
import { WorkoutVolume } from './WorkoutVolume';
import { WorkoutDuration } from '../workout/WorkoutDuration';
import { ExerciseStatusBadge, WorkoutStatusBadge } from '../workout/ExerciseStatusBadge';

export interface HistoryPlan {
  planId: string;
  /** Most recent name stored in the workouts (historical snapshot). */
  planName: string;
  active: boolean;
  totalWorkouts: number;
  completedWorkouts: number;
  interruptedWorkouts: number;
  inProgressWorkouts: number;
  firstWorkoutDate: string;
  lastWorkoutDate: string;
}

function useHistoryPlans(filters: HistoryFilters, enabled: boolean) {
  return useQuery({
    queryKey: ['me', 'history', 'plans', filters],
    queryFn: ({ signal }) => http.get<HistoryPlan[]>('/api/me/workout-plans', { ...filters }, signal),
    enabled,
  });
}

function useHistory(page: number, scope: HistoryScope, enabled: boolean) {
  return useQuery({
    queryKey: ['me', 'history', page, scope],
    queryFn: ({ signal }) => http.get<Page<WorkoutSummary>>('/api/me/workouts', { page, size: 20, ...scope }, signal),
    enabled,
  });
}

/** US-24 + Task 4: history organised by plan, then by session, date and exercises. */
export function HistoryPage() {
  const [params, setParams] = useSearchParams();
  const plan = params.get('plan') ?? '';
  const filters = readHistoryFilters(params);
  const invalid = historyFilterError(filters);
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
      <PageHeader title="Storico" subtitle={plan ? 'Allenamenti della scheda selezionata.' : 'Scegli una scheda per vedere i suoi allenamenti.'} />
      <HistoryFilterForm key={search} filters={filters} onApply={apply}
        onReset={() => apply({ from: '', to: '', status: '', q: '' })} />
      {invalid ? <Alert tone="error"><p>{invalid}</p></Alert>
        : plan ? <PlanHistory planId={plan} filters={filters} />
          : <HistoryPlanList filters={filters} />}
    </>
  );
}

const withParams = (params: URLSearchParams, changes: Record<string, string | null>) => {
  const next = new URLSearchParams(params);
  for (const [key, value] of Object.entries(changes)) {
    if (value) next.set(key, value); else next.delete(key);
  }
  const text = next.toString();
  return text ? `?${text}` : '';
};

/** First level: the plans that have workouts, active or not. */
function HistoryPlanList({ filters }: { filters: HistoryFilters }) {
  const [params] = useSearchParams();
  const query = useHistoryPlans(filters, true);
  const filtered = HISTORY_FILTER_KEYS.some((key) => !!filters[key]);
  return <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
    {query.data && query.data.length === 0 ? (
      <EmptyState title={filtered ? 'Nessun allenamento corrisponde ai filtri' : 'Nessun allenamento ancora'} icon={<History size={40} />}>
        <p>{filtered ? 'Modifica o azzera i filtri per cercare altri allenamenti.' : 'Gli allenamenti svolti compariranno qui, divisi per scheda.'}</p>
      </EmptyState>
    ) : (
      <section aria-labelledby="history-plans-title" className="stack">
        <h2 id="history-plans-title" style={{ fontSize: 'var(--text-xl)', margin: 0 }}>Schede con allenamenti</h2>
        <ul className="list" aria-label="Schede con allenamenti">
          {query.data?.map((plan) => (
            <li key={plan.planId} className="list-item">
              <div className="list-item__main">
                <div className="list-item__title">
                  <Link to={withParams(params, { plan: plan.planId, page: null })}>{plan.planName}</Link>
                </div>
                <div className="list-item__meta">
                  {plan.totalWorkouts} {plan.totalWorkouts === 1 ? 'allenamento' : 'allenamenti'} · ultimo il {formatLongDate(plan.lastWorkoutDate)}
                </div>
                <div className="list-item__meta">
                  {plan.completedWorkouts} completati{plan.interruptedWorkouts ? `, ${plan.interruptedWorkouts} interrotti` : ''}
                  {plan.inProgressWorkouts ? `, ${plan.inProgressWorkouts} in corso` : ''}
                </div>
              </div>
              <StatusBadge tone={plan.active ? 'success' : 'neutral'}>{plan.active ? 'Attiva' : 'Non attiva'}</StatusBadge>
            </li>
          ))}
        </ul>
      </section>
    )}
  </QueryState>;
}

/** Second level: one plan, grouped by session (expandable), newest first inside each page. */
function PlanHistory({ planId, filters }: { planId: string; filters: HistoryFilters }) {
  const [params, setParams] = useSearchParams();
  const page = historyPage(params);
  const scope: HistoryScope = { ...filters, plan: planId };
  const query = useHistory(page, scope, true);
  const plans = useHistoryPlans(filters, true);
  const plan = plans.data?.find((p) => p.planId === planId);
  const search = params.toString();
  const filtered = HISTORY_FILTER_KEYS.some((key) => !!filters[key]);
  const changePage = (nextPage: number) => {
    const next = new URLSearchParams(params);
    if (nextPage) next.set('page', String(nextPage)); else next.delete('page');
    setParams(next);
  };
  const groups = new Map<string, WorkoutSummary[]>();
  query.data?.content.forEach((w) => groups.set(w.sessionTitle, [...(groups.get(w.sessionTitle) ?? []), w]));
  return <div className="stack">
    <div className="row row--between history-plan-header">
      <div style={{ minWidth: 0 }}>
        <Link to={`/app/history${withParams(params, { plan: null, page: null })}`} className="small">← Tutte le schede</Link>
        <h2 style={{ margin: 0, overflowWrap: 'anywhere' }}>{plan?.planName ?? query.data?.content[0]?.planName ?? 'Scheda'}</h2>
      </div>
      {plan ? <StatusBadge tone={plan.active ? 'success' : 'neutral'}>{plan.active ? 'Attiva' : 'Non attiva'}</StatusBadge> : null}
    </div>
    <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
      {query.data && query.data.content.length === 0 ? (
        <EmptyState title={query.data.totalElements > 0 ? 'Nessun allenamento in questa pagina'
          : filtered ? 'Nessun allenamento corrisponde ai filtri' : 'Nessun allenamento per questa scheda'} icon={<History size={40} />}>
          {query.data.totalElements > 0 ? <Button variant="secondary" onClick={() => changePage(0)}>Vai alla prima pagina</Button> : null}
        </EmptyState>
      ) : (
        <>
          {query.data ? <p className="small muted" role="status">{query.data.totalElements} allenamenti</p> : null}
          <div className="stack" aria-label="Allenamenti per sessione">
            {[...groups].map(([title, items]) => (
              <details key={title} className="card history-group" open>
                <summary className="history-group__summary">
                  <span className="history-group__title">{title}</span>
                  <span className="small muted">{items.length} {items.length === 1 ? 'allenamento' : 'allenamenti'}</span>
                </summary>
                <ul className="list" aria-label="Allenamenti">
                  {items.map((w) => (
                    <li key={w.id} className="list-item">
                      <div className="list-item__main">
                        <div className="list-item__title">
                          <Link to={`/app/history/${w.id}${search ? `?${search}` : ''}`}>{formatLongDate(w.scheduledDate)}</Link>
                        </div>
                        <div className="list-item__meta">
                          {w.completedExercises} completati{w.skippedExercises ? `, ${w.skippedExercises} saltati` : ''} su {w.totalExercises}
                        </div>
                        <div className="list-item__meta"><WorkoutDuration status={w.status} seconds={w.durationSeconds} /></div>
                        <div className="list-item__meta"><WorkoutVolume volume={w.volume} /></div>
                      </div>
                      <WorkoutStatusBadge status={w.status} />
                    </li>
                  ))}
                </ul>
              </details>
            ))}
          </div>
          <Pagination page={query.data?.page ?? 0} totalPages={query.data?.totalPages ?? 0} onChange={changePage} />
        </>
      )}
    </QueryState>
    {/* On phones the workouts come first; summary and charts of the same plan follow. */}
    <HistoryStats filters={scope} />
    <HistoryProgress filters={scope} />
  </div>;
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
              <section key={e.id} id={`exercise-${e.id}`} className="card" aria-label={e.exerciseName}>
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

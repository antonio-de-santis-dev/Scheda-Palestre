import { useQuery } from '@tanstack/react-query';
import { http } from '../../shared/api/http';
import { QueryState } from '../../shared/components/States';
import { formatWorkoutDuration } from '../../shared/utils/format';
import type { VolumeSummary } from '../workout/api';
import { WorkoutVolume } from './WorkoutVolume';
import type { HistoryFilters } from './filters';

export interface HistoryStatsData {
  totalWorkouts: number;
  completedWorkouts: number;
  interruptedWorkouts: number;
  inProgressWorkouts: number;
  recordedDurationSeconds: number | null;
  workoutsWithDuration: number;
  workoutsMissingDuration: number;
  volume: VolumeSummary;
}

export function HistoryStats({ filters }: { filters: HistoryFilters }) {
  const query = useQuery({
    queryKey: ['me', 'history', 'stats', filters],
    queryFn: () => http.get<HistoryStatsData>('/api/me/workout-stats', { ...filters }),
  });
  const stats = query.data;
  return (
    <section className="card history-stats" aria-labelledby="history-stats-title">
      <h2 id="history-stats-title" style={{ fontSize: 'var(--text-xl)', margin: 0 }}>Riepilogo dei risultati filtrati</h2>
      <p className="small muted">Comprende tutti gli allenamenti che corrispondono ai filtri, anche nelle altre pagine.</p>
      <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()} loadingLabel="Caricamento riepilogo…">
        {stats ? <>
          <dl className="history-stats__counts">
            <div><dt>Allenamenti</dt><dd>{stats.totalWorkouts}</dd></div>
            <div><dt>Completati</dt><dd>{stats.completedWorkouts}</dd></div>
            <div><dt>Interrotti</dt><dd>{stats.interruptedWorkouts}</dd></div>
            <div><dt>In corso</dt><dd>{stats.inProgressWorkouts}</dd></div>
          </dl>
          <p className="small">Tempo registrato: {formatWorkoutDuration(stats.recordedDurationSeconds)}</p>
          <p className="small muted">Durata disponibile per {stats.workoutsWithDuration} allenamenti conclusi.
            {stats.workoutsMissingDuration > 0 ? ` Non disponibile per ${stats.workoutsMissingDuration}.` : ''}
            {stats.inProgressWorkouts > 0 ? ' Gli allenamenti in corso non contribuiscono alla durata.' : ''}</p>
          <WorkoutVolume volume={stats.volume} detailed />
        </> : null}
      </QueryState>
    </section>
  );
}

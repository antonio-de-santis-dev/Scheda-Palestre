import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import { http } from '../../shared/api/http';
import { QueryState } from '../../shared/components/States';
import { formatDateTime } from '../../shared/utils/format';
import type { ExerciseIdentity } from '../workout/api';
import type { HistoryFilters } from './filters';

export interface SetRecord {
  workoutId: string; exerciseSnapshotId: string; setId: string; setIndex: number;
  scheduledDate: string; completedAt: string; planName: string; sessionTitle: string;
  exerciseName: string; weightKgUsed: number | null; repsActual: number | null;
}
export interface ExerciseRecords {
  identity: ExerciseIdentity; exerciseName: string; muscleGroupName: string;
  completedSets: number; recordedWeightSets: number; recordedRepsSets: number;
  weightRecord: SetRecord | null; repsRecord: SetRecord | null;
}
const weight = (value: number | null) => value == null ? 'Peso non registrato' : `${value.toLocaleString('it-IT', { maximumFractionDigits: 2 })} kg`;
const reps = (value: number | null) => value == null ? 'Ripetizioni non registrate' : `${value} ripetizioni`;

function RecordResult({ record, metric, search }: { record: SetRecord | null; metric: 'weight' | 'reps'; search: string }) {
  return <div className="stack" style={{ gap: 'var(--space-2)' }}>
    <h4 style={{ margin: 0 }}>{metric === 'weight' ? 'Peso massimo' : 'Ripetizioni massime'}</h4>
    {record ? <>
      <strong>{metric === 'weight' ? weight(record.weightKgUsed) : reps(record.repsActual)}</strong>
      <span className="small">Nella stessa serie: {metric === 'weight' ? reps(record.repsActual) : weight(record.weightKgUsed)}</span>
      <span className="small muted">{record.exerciseName} · serie {record.setIndex} · {formatDateTime(record.completedAt)}</span>
      <Link to={`/app/history/${record.workoutId}${search ? `?${search}` : ''}#exercise-${record.exerciseSnapshotId}`}>
        {record.planName} · {record.sessionTitle}
      </Link>
    </> : <span className="muted">Non registrato</span>}
  </div>;
}

export function HistoryRecords({ filters }: { filters: HistoryFilters }) {
  const [params] = useSearchParams();
  const query = useQuery({ queryKey: ['me', 'history', 'records', filters],
    queryFn: ({ signal }) => http.get<ExerciseRecords[]>('/api/me/workout-records', { ...filters }, signal) });
  return <section className="card" aria-labelledby="history-records-title">
    <h2 id="history-records-title" style={{ fontSize: 'var(--text-xl)', margin: 0 }}>Record nei filtri selezionati</h2>
    <p className="small muted">Massimi indipendenti delle serie completate, anche nelle altre pagine. A parità di valore viene mostrata la prima serie registrata. Senza filtri comprendono tutto lo storico.</p>
    <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()} loadingLabel="Caricamento record…">
      {query.data?.length === 0 ? <p className="muted">Nessuna serie completata nei filtri selezionati.</p> : null}
      <div className="stack">
        {query.data?.map((exercise) => <article className="card" key={`${exercise.identity.source}:${exercise.identity.id}`} aria-label={`${exercise.exerciseName} · ${exercise.muscleGroupName}`}>
          <h3 style={{ margin: 0 }}>{exercise.exerciseName}</h3>
          <p className="small muted">{exercise.muscleGroupName}</p>
          <div className="history-records__results">
            <RecordResult record={exercise.weightRecord} metric="weight" search={params.toString()} />
            <RecordResult record={exercise.repsRecord} metric="reps" search={params.toString()} />
          </div>
          <p className="small muted">Peso registrato in {exercise.recordedWeightSets}/{exercise.completedSets} serie completate; ripetizioni in {exercise.recordedRepsSets}/{exercise.completedSets}.</p>
          {exercise.identity.source === 'LEGACY' ? <p className="small muted">Identità storica limitata: confronto solo con le registrazioni che conservano la stessa identità.</p> : null}
        </article>)}
      </div>
    </QueryState>
  </section>;
}

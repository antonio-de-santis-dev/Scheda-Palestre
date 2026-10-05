import { useId } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';
import { http } from '../../shared/api/http';
import { QueryState } from '../../shared/components/States';
import { formatWorkoutDuration } from '../../shared/utils/format';
import type { HistoryFilters } from './filters';
import type { HistoryStatsData } from './HistoryStats';

export interface MonthlyProgress { month: string; totals: HistoryStatsData }
type Metric = 'volume' | 'duration' | 'workouts';
const labels: Record<Metric, string> = { volume: 'Volume registrato', duration: 'Durata registrata', workouts: 'Allenamenti' };
const units: Record<Metric, string> = { volume: 'kg × ripetizioni', duration: 'minuti', workouts: 'allenamenti' };
const monthFormatter = new Intl.DateTimeFormat('it-IT', { month: 'short', year: 'numeric', timeZone: 'UTC' });
const monthLabel = (month: string) => monthFormatter.format(new Date(`${month}T00:00:00Z`));
const number = (value: number) => value.toLocaleString('it-IT', { maximumFractionDigits: 2 });

function valueOf(point: MonthlyProgress, metric: Metric): number | null {
  if (metric === 'volume') return point.totals.volume.recordedKgReps;
  if (metric === 'duration') return point.totals.recordedDurationSeconds == null ? null : point.totals.recordedDurationSeconds / 60;
  return point.totals.totalWorkouts;
}

function partial(point: MonthlyProgress, metric: Metric): boolean {
  return metric === 'volume' ? point.totals.volume.recordedSets < point.totals.volume.completedSets
    : metric === 'duration' ? point.totals.workoutsMissingDuration > 0 : false;
}

function exactValue(point: MonthlyProgress, metric: Metric): string {
  if (metric === 'duration') return formatWorkoutDuration(point.totals.recordedDurationSeconds);
  const value = valueOf(point, metric);
  return value == null ? 'Non disponibile' : `${number(value)} ${units[metric]}`;
}

function MonthlyChart({ points, metric }: { points: MonthlyProgress[]; metric: Metric }) {
  const patternId = useId().replaceAll(':', '');
  const maximum = Math.max(1, ...points.map((point) => valueOf(point, metric) ?? 0));
  const width = Math.max(360, 70 + points.length * 76);
  const step = (width - 70) / points.length;
  return <div className="history-chart-scroll" tabIndex={0} role="region" aria-label={`Grafico: ${labels[metric]}. Scorri per vedere tutti i mesi.`}>
    <svg width={width} height="290" viewBox={`0 0 ${width} 290`} role="img" aria-label={`${labels[metric]} per mese, in ${units[metric]}`}>
      <defs><pattern id={patternId} width="8" height="8" patternUnits="userSpaceOnUse">
        <rect width="8" height="8" fill="var(--color-primary)" />
        <path d="M0 8L8 0" stroke="var(--color-surface)" strokeWidth="2" />
      </pattern></defs>
      {[0, 0.5, 1].map((fraction) => <g key={fraction}>
        <line x1="60" x2={width - 10} y1={220 - fraction * 170} y2={220 - fraction * 170} stroke="var(--color-border)" />
        <text x="54" y={224 - fraction * 170} textAnchor="end" className="history-chart-label">{number(maximum * fraction)}</text>
      </g>)}
      <text x="60" y="22" className="history-chart-label">{units[metric]}</text>
      {points.map((point, index) => {
        const value = valueOf(point, metric);
        const x = 60 + index * step + step / 2;
        const height = value == null ? 0 : value / maximum * 170;
        const description = `${monthLabel(point.month)}: ${exactValue(point, metric)}${partial(point, metric) ? ', dati parziali' : ''}`;
        return <g key={point.month} role="img" aria-label={description}>
          <title>{description}</title>
          {value == null ? <text x={x} y="207" textAnchor="middle" className="history-chart-label">N/D</text>
            : value === 0 ? <circle cx={x} cy="220" r="3" fill="var(--color-primary)" />
              : <rect x={x - 18} y={220 - height} width="36" height={height}
                fill={partial(point, metric) ? `url(#${patternId})` : 'var(--color-primary)'} />}
          {value != null ? <text x={x} y={Math.max(38, 210 - height)} textAnchor="middle" className="history-chart-label">{number(value)}{partial(point, metric) ? '*' : ''}</text> : null}
          <text x={x} y="249" textAnchor="middle" className="history-chart-label">{point.month.slice(5, 7)}/{point.month.slice(0, 4)}</text>
        </g>;
      })}
    </svg>
  </div>;
}

export function HistoryProgress({ filters }: { filters: HistoryFilters }) {
  const [params, setParams] = useSearchParams();
  const requested = params.get('chart');
  const metric: Metric = requested === 'duration' || requested === 'workouts' ? requested : 'volume';
  const query = useQuery({ queryKey: ['me', 'history', 'progress', filters],
    queryFn: () => http.get<MonthlyProgress[]>('/api/me/workout-progress', { ...filters }) });
  return <section className="card history-progress" aria-labelledby="history-progress-title">
    <h2 id="history-progress-title" style={{ fontSize: 'var(--text-xl)', margin: 0 }}>Progressi mensili</h2>
    <p className="small muted">Tutti i risultati dei filtri, indipendentemente dalla pagina. Sono mostrati solo i mesi con allenamenti.</p>
    <label className="field">Dato del grafico<select className="input" value={metric} onChange={(event) => {
      const next = new URLSearchParams(params);
      if (event.target.value === 'volume') next.delete('chart'); else next.set('chart', event.target.value);
      setParams(next);
    }}>
      <option value="volume">Volume registrato</option><option value="duration">Durata registrata</option><option value="workouts">Allenamenti</option>
    </select></label>
    <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()} loadingLabel="Caricamento progressi…">
      {query.data?.length ? <>
        <MonthlyChart points={query.data} metric={metric} />
        <p className="small muted">* e tratteggio: dati parziali. N/D: valore non disponibile; un punto sulla linea di base indica zero registrato.
          {metric === 'duration' ? ' La durata include soltanto allenamenti conclusi con timestamp validi.' : null}
          {metric === 'volume' ? ' Il volume include solo serie completate con peso e ripetizioni registrati, anche in allenamenti in corso.' : null}</p>
        <details><summary>Dati mensili e copertura</summary><div className="table-scroll"><table className="sets-table">
          <caption className="visually-hidden">Dati dei progressi mensili</caption>
          <thead><tr><th scope="col">Mese</th><th scope="col">Allenamenti</th><th scope="col">Esiti</th>
            <th scope="col">Durata registrata</th><th scope="col">Copertura durata</th><th scope="col">Volume registrato</th><th scope="col">Copertura volume</th></tr></thead>
          <tbody>{query.data.map((point) => <tr key={point.month}>
            <th scope="row">{monthLabel(point.month)}</th><td>{point.totals.totalWorkouts}</td>
            <td>{point.totals.completedWorkouts} completati, {point.totals.interruptedWorkouts} interrotti, {point.totals.inProgressWorkouts} in corso</td>
            <td>{exactValue(point, 'duration')}</td><td>{point.totals.workoutsWithDuration} con durata; {point.totals.workoutsMissingDuration} mancanti</td>
            <td>{exactValue(point, 'volume')}</td><td>{point.totals.volume.recordedSets}/{point.totals.volume.completedSets} serie con dati completi</td>
          </tr>)}</tbody>
        </table></div></details>
      </> : query.data ? <p className="small muted">Nessun allenamento nei filtri selezionati per il grafico.</p> : null}
    </QueryState>
  </section>;
}

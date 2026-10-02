import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { http } from '../../shared/api/http';
import { PageHeader } from '../../shared/components/PageHeader';
import { QueryState } from '../../shared/components/States';
import { formatDate, formatWorkoutDuration } from '../../shared/utils/format';
import { formatWeight } from '../../shared/utils/weight';

export interface Progress {
  from: string; to: string; generatedOn: string;
  days: { date: string; completed: number; interrupted: number; inProgress: number; durationSeconds: number; closedWorkouts: number }[];
  exerciseDays: { key: string; name: string; muscleGroup: string; date: string; completedSets: number; volumeSets: number;
    volumeKg: number | null; maxWeightKg: number | null; maxReps: number | null }[];
  records: { key: string; name: string; muscleGroup: string; maxWeightKg: number | null; maxReps: number | null }[];
}

const number = (value: number) => value.toLocaleString('it-IT', { maximumFractionDigits: 2 });

/** Discrete observations, no interpolation or conversion of absent measurements to zero. */
function Bars({ title, unit, points }: { title: string; unit: string; points: { date: string; value: number }[] }) {
  const max = Math.max(1, ...points.map((p) => p.value));
  const width = Math.max(600, points.length * 28);
  const step = (width - 70) / Math.max(1, points.length);
  return <figure className="progress-chart">
    <figcaption>{title}</figcaption>
    {points.length ? <>
      <div className="table-scroll">
        <svg viewBox={`0 0 ${width} 220`} style={{ minWidth: width }} role="img" aria-label={`${title}. Valori esatti nella tabella seguente.`}>
          <text x="0" y="20">{number(max)} {unit}</text>
          <line x1="55" x2={width} y1="180" y2="180" className="progress-chart__axis" />
          <text x="25" y="183">0</text>
          {points.map((p, i) => <g key={p.date}>
            <rect x={55 + i * step + step * .15} y={180 - p.value / max * 145} width={step * .7}
              height={Math.max(1, p.value / max * 145)} className="progress-chart__bar">
              <title>{formatDate(p.date)}: {number(p.value)} {unit}</title>
            </rect>
            {i % Math.max(1, Math.ceil(points.length / 6)) === 0 ? <text x={55 + i * step} y="210">{formatDate(p.date)}</text> : null}
          </g>)}
        </svg>
      </div>
      <details><summary>Mostra i valori del grafico</summary><div className="table-scroll">
        <table className="table"><caption>{title} · {unit}</caption><thead><tr><th scope="col">Data</th><th scope="col">Valore</th></tr></thead>
          <tbody>{points.map((p) => <tr key={p.date}><th scope="row">{formatDate(p.date)}</th><td>{number(p.value)} {unit}</td></tr>)}</tbody>
        </table>
      </div></details>
    </> : <p className="muted">Nessun valore registrato nel periodo.</p>}
  </figure>;
}

export function ProgressPage() {
  const [params, setParams] = useSearchParams();
  const from = params.get('from') ?? '';
  const to = params.get('to') ?? '';
  const [error, setError] = useState('');
  const query = useQuery({ queryKey: ['me', 'progress', from, to], queryFn: () => http.get<Progress>('/api/me/progress', { from, to }) });
  return <>
    <PageHeader title="Statistiche e record" subtitle="I tuoi progressi, dai risultati che hai registrato." back={{ to: '/app/history', label: 'Storico' }} />
    <form key={`${from}:${to}:${query.data?.from}`} className="card progress-filters" aria-label="Periodo statistiche" onSubmit={(event) => {
      event.preventDefault();
      const data = new FormData(event.currentTarget);
      const start = String(data.get('from')); const end = String(data.get('to'));
      const days = (Date.parse(end) - Date.parse(start)) / 86400000;
      if (!Number.isFinite(days) || days < 0 || days > 365) { setError('Scegli un periodo da 1 a 366 giorni.'); return; }
      setError(''); setParams({ from: start, to: end });
    }}>
      <label className="field">Dal<input type="date" name="from" required defaultValue={from || query.data?.from} /></label>
      <label className="field">Al<input type="date" name="to" required defaultValue={to || query.data?.to} /></label>
      <button className="btn btn--primary" type="submit">Aggiorna periodo</button>
      <button className="btn btn--secondary" type="button" onClick={() => { setError(''); setParams({}); }}>Ultime 12 settimane</button>
      {error ? <p role="alert">{error}</p> : null}
    </form>
    <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
      {query.data ? <ProgressBody report={query.data} /> : null}
    </QueryState>
  </>;
}

function ProgressBody({ report }: { report: Progress }) {
  const [selection, setSelection] = useState('');
  const [metric, setMetric] = useState<'maxWeightKg' | 'maxReps' | 'volumeKg'>('maxWeightKg');
  const choices = [...new Map(report.exerciseDays.map((e) => [e.key, e])).values()];
  const key = choices.some((e) => e.key === selection) ? selection : choices[0]?.key ?? '';
  const totals = report.days.reduce((a, d) => ({ completed: a.completed + d.completed, interrupted: a.interrupted + d.interrupted,
    inProgress: a.inProgress + d.inProgress, seconds: a.seconds + d.durationSeconds, closed: a.closed + d.closedWorkouts }),
  { completed: 0, interrupted: 0, inProgress: 0, seconds: 0, closed: 0 });
  const sets = report.exerciseDays.reduce((a, e) => a + e.completedSets, 0);
  const known = report.exerciseDays.reduce((a, e) => a + e.volumeSets, 0);
  const volume = report.exerciseDays.reduce((a, e) => a + (e.volumeKg ?? 0), 0);
  const selectedDays = report.exerciseDays.filter((e) => e.key === key);
  const metricLabel = { maxWeightKg: 'Carico massimo giornaliero', maxReps: 'Ripetizioni massime per serie', volumeKg: 'Volume giornaliero registrato' }[metric];
  const points = selectedDays.flatMap((e) => e[metric] == null ? [] : [{ date: e.date, value: e[metric] }]);
  // Scheduled dates are date-only strings, so UTC arithmetic avoids DST changing bucket sizes.
  const activity: { date: string; value: number }[] = [];
  for (let day = Date.parse(report.from); day <= Date.parse(report.to); day += 86400000) {
    const date = new Date(day).toISOString().slice(0, 10);
    const d = report.days.find((item) => item.date === date);
    activity.push({ date, value: (d?.completed ?? 0) + (d?.interrupted ?? 0) });
  }
  return <div className="stack">
    <p className="muted small">Periodo: {formatDate(report.from)} – {formatDate(report.to)}. Raggruppamento per data dell’allenamento; include le serie completate degli allenamenti interrotti e in corso.</p>
    <dl className="progress-totals">
      <div className="card"><dt>Allenamenti completati</dt><dd>{totals.completed}</dd></div>
      <div className="card"><dt>Interrotti / in corso</dt><dd>{totals.interrupted} / {totals.inProgress}</dd></div>
      <div className="card"><dt>Serie completate</dt><dd>{sets}</dd></div>
      <div className="card"><dt>Tempo degli allenamenti conclusi</dt><dd>{formatWorkoutDuration(totals.seconds)}</dd></div>
      <div className="card"><dt>Durata media dei conclusi</dt><dd>{totals.closed ? formatWorkoutDuration(Math.round(totals.seconds / totals.closed)) : 'Non disponibile'}</dd></div>
      <div className="card"><dt>Volume registrato</dt><dd>{known ? `${number(volume)} kg × rip.` : 'Non disponibile'}</dd></div>
    </dl>
    <p className="small muted">Volume calcolabile per {known} su {sets} serie: richiede sia peso usato sia ripetizioni effettive. I valori previsti non sostituiscono quelli mancanti. La durata include recuperi e tempo fuori dalla pagina.</p>
    {!report.days.length ? <p>Nessun allenamento nel periodo. <Link to="/app/today">Vai a Oggi</Link></p> : null}
    <section className="card" aria-label="Attività nel periodo"><Bars title="Allenamenti conclusi per giorno" unit="allenamenti" points={activity} /></section>
    <section className="card stack" aria-labelledby="exercise-progress-title">
      <h2 id="exercise-progress-title">Progressi per esercizio</h2>
      {choices.length ? <>
        <div className="progress-filters">
          <label className="field">Esercizio<select value={key} onChange={(event) => setSelection(event.target.value)}>
            {choices.map((e) => <option key={e.key} value={e.key}>{e.name} · {e.muscleGroup}{e.key.startsWith('legacy:') ? ' · storico della scheda' : ''}</option>)}
          </select></label>
          <label className="field">Metrica<select value={metric} onChange={(event) => setMetric(event.target.value as typeof metric)}>
            <option value="maxWeightKg">Carico massimo</option><option value="maxReps">Ripetizioni massime</option><option value="volumeKg">Volume registrato</option>
          </select></label>
        </div>
        <Bars title={metricLabel} unit={metric === 'maxReps' ? 'rip.' : metric === 'volumeKg' ? 'kg × rip.' : 'kg'} points={points} />
        <p className="small muted">Le date senza la misura scelta non compaiono nel grafico. Carico e ripetizioni massimi possono provenire da serie diverse.</p>
      </> : <p>Nessuna serie completata nel periodo.</p>}
    </section>
    <section className="card" aria-labelledby="records-title">
      <h2 id="records-title">Record personali</h2>
      <p className="small muted">Massimi registrati in tutto lo storico, indipendenti dal periodo scelto. Peso e ripetizioni sono record distinti, anche da serie diverse; non stimano forza o massimale.</p>
      {report.records.length ? <div className="table-scroll"><table className="table"><caption>Record registrati per esercizio</caption>
        <thead><tr><th scope="col">Esercizio</th><th scope="col">Carico massimo</th><th scope="col">Ripetizioni massime</th></tr></thead>
        <tbody>{report.records.map((e) => <tr key={e.key}><th scope="row">{e.name}<span className="small muted" style={{ display: 'block' }}>{e.muscleGroup}{e.key.startsWith('legacy:') ? ' · storico della scheda' : ''}</span></th>
          <td>{e.maxWeightKg == null ? 'Non registrato' : formatWeight(e.maxWeightKg)}</td><td>{e.maxReps ?? 'Non registrate'}</td></tr>)}</tbody>
      </table></div> : <p>Nessun record ancora. Registra peso o ripetizioni a fine serie per iniziare.</p>}
    </section>
  </div>;
}

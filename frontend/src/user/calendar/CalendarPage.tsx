import { useState } from 'react';
import { Link } from 'react-router';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { PageHeader } from '../../shared/components/PageHeader';
import { Button } from '../../shared/components/Button';
import { QueryState } from '../../shared/components/States';
import { StatusBadge } from '../../shared/components/StatusBadge';
import { PlanSessionView } from '../../shared/components/PlanSessionView';
import { addDays, formatDate, formatLongDate, parseLocalDate, todayIso, toIsoDate } from '../../shared/utils/format';
import { useCalendar, type CalendarDay } from '../workout/api';
import { WorkoutStatusBadge } from '../workout/ExerciseStatusBadge';
import { useMyPlan } from '../plans/api';

const monthFormatter = new Intl.DateTimeFormat('it-IT', { month: 'long', year: 'numeric' });

/** The grid includes whole weeks; the backend accepts up to 62 days per request. */
export function CalendarPage() {
  const today = todayIso();
  const [month, setMonth] = useState(() => today.slice(0, 7) + '-01');
  const [selected, setSelected] = useState(today);
  const first = parseLocalDate(month);
  const last = new Date(first.getFullYear(), first.getMonth() + 1, 0);
  const from = addDays(month, -((first.getDay() + 6) % 7));
  const to = addDays(toIsoDate(last), 6 - ((last.getDay() + 6) % 7));
  const query = useCalendar(from, to);
  const days = new Map(query.data?.map((day) => [day.date, day]));
  const cells: string[] = [];
  for (let date = from; date <= to; date = addDays(date, 1)) cells.push(date);
  const selectedDate = selected >= from && selected <= to ? selected : month;
  const day = days.get(selectedDate);
  const moveMonth = (delta: number) => {
    const next = new Date(first.getFullYear(), first.getMonth() + delta, 1);
    setMonth(toIsoDate(next));
    setSelected(toIsoDate(next));
  };

  return <>
    <PageHeader title="Calendario" subtitle={monthFormatter.format(first)} actions={<Link to="/app/schedule" className="btn btn--secondary btn--sm">Modifica giorni</Link>} />
    <div className="calendar-layout">
      <section className="card" aria-label="Calendario mensile">
        <nav className="calendar-controls" aria-label="Periodo">
          <Button variant="secondary" className="icon-btn" aria-label="Mese precedente" onClick={() => moveMonth(-1)}><ChevronLeft size={20} aria-hidden="true" /></Button>
          <h2>{monthFormatter.format(first)}</h2>
          <Button variant="secondary" className="icon-btn" aria-label="Mese successivo" onClick={() => moveMonth(1)}><ChevronRight size={20} aria-hidden="true" /></Button>
        </nav>
        <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
          <div className="month-weekdays" aria-hidden="true">{['L', 'M', 'M', 'G', 'V', 'S', 'D'].map((label, i) => <span key={i}>{label}</span>)}</div>
          <ol className="month-grid" aria-label="Giorni">
            {cells.map((date) => {
              const item = days.get(date);
              const training = item?.type === 'TRAINING' || !!item?.workout;
              const completed = item?.workout?.status === 'COMPLETED';
              const outside = date.slice(0, 7) !== month.slice(0, 7);
              const label = item?.workout?.sessionTitle ?? item?.sessionTitle;
              return <li key={date}><button type="button"
                className={`month-day${training ? ' month-day--training' : ''}${completed ? ' month-day--done' : ''}${date === today ? ' month-day--today' : ''}${outside ? ' month-day--outside' : ''}`}
                aria-label={`${formatDate(date)}, ${training ? label : 'riposo'}${completed ? ', completato' : ''}`}
                aria-current={date === today ? 'date' : undefined} aria-pressed={date === selectedDate}
                onClick={() => setSelected(date)}>
                <span>{parseLocalDate(date).getDate()}</span>
                <span className="month-day__session" aria-hidden="true">{training ? label : ''}</span>
                {training ? <span className="month-day__dot" aria-hidden="true" /> : null}
              </button></li>;
            })}
          </ol>
        </QueryState>
        <div className="calendar-legend"><StatusBadge tone="success">Svolto</StatusBadge><StatusBadge tone="primary">Previsto</StatusBadge><span className="small muted">Seleziona un giorno per i dettagli</span></div>
        <Button variant="ghost" size="sm" onClick={() => { setMonth(today.slice(0, 7) + '-01'); setSelected(today); }}>Torna a oggi</Button>
      </section>
      <CalendarDetail key={selectedDate} day={day} date={selectedDate} today={today} />
    </div>
  </>;
}

function CalendarDetail({ day, date, today }: { day: CalendarDay | undefined; date: string; today: string }) {
  const plan = useMyPlan(day?.assignmentId ?? '');
  // Calendar responses identify sessions by title; duplicated titles are ambiguous.
  const matchingSessions = plan.data?.sessions.filter((s) => s.title === day?.sessionTitle);
  const session = matchingSessions?.length === 1 ? matchingSessions[0] : undefined;
  return <section className="card calendar-detail" aria-labelledby="calendar-detail-title">
    <div className="row row--between"><h2 id="calendar-detail-title">{formatLongDate(date)}</h2>
      {day?.workout ? <WorkoutStatusBadge status={day.workout.status} /> : date === today ? <StatusBadge tone="warning">Oggi</StatusBadge> : null}
    </div>
    {day?.workout ? <>
      <h3>{day.workout.sessionTitle}</h3><p className="muted">{day.workout.planName}</p>
      <Link className="btn btn--secondary" to={day.workout.status === 'IN_PROGRESS' ? `/app/workout/${day.workout.id}` : `/app/history/${day.workout.id}`}
        aria-label={`Apri allenamento del ${formatDate(date)}`}>{day.workout.status === 'IN_PROGRESS' ? 'Riprendi allenamento' : 'Vedi il riepilogo'}</Link>
    </> : day?.type === 'TRAINING' ? <>
      <h3>{day.sessionTitle}</h3><p className="muted">{day.planName}</p>
      {date < today ? <StatusBadge tone="neutral">Non svolto</StatusBadge> : <StatusBadge tone="primary">Previsto</StatusBadge>}
      <QueryState isLoading={plan.isLoading} error={plan.error} onRetry={() => void plan.refetch()}>
        {session ? <PlanSessionView session={session} headingLevel={3} hideTitle /> : null}
      </QueryState>
      {date === today ? <Link to="/app/today" className="btn btn--primary btn--block">Vai all’allenamento di oggi</Link>
        : day.assignmentId ? <Link to={`/app/plans/${day.assignmentId}`} className="btn btn--secondary">Vedi scheda completa</Link> : null}
    </> : <p className="muted">{day?.type === 'REST' ? 'Giorno di riposo. Recupera le energie per la prossima sessione.' : 'Nessun allenamento programmato per questa data.'}</p>}
  </section>;
}

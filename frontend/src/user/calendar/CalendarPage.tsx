import { useState } from 'react';
import { Link } from 'react-router';
import { ChevronLeft, ChevronRight, CircleSlash } from 'lucide-react';
import { PageHeader } from '../../shared/components/PageHeader';
import { Button } from '../../shared/components/Button';
import { QueryState } from '../../shared/components/States';
import { StatusBadge } from '../../shared/components/StatusBadge';
import { addDays, formatDate, parseLocalDate, todayIso, toIsoDate } from '../../shared/utils/format';
import { useCalendar, type CalendarDay } from '../workout/api';
import { WorkoutStatusBadge } from '../workout/ExerciseStatusBadge';

const DAYS = 28;
const weekdayFormatter = new Intl.DateTimeFormat('it-IT', { weekday: 'short' });
const monthFormatter = new Intl.DateTimeFormat('it-IT', { month: 'short' });

function mondayOf(iso: string): string {
  const date = parseLocalDate(iso);
  const dow = (date.getDay() + 6) % 7;
  date.setDate(date.getDate() - dow);
  return toIsoDate(date);
}

/** Future sessions and past outcomes, four weeks at a time. */
export function CalendarPage() {
  const today = todayIso();
  const [from, setFrom] = useState(() => mondayOf(today));
  const to = addDays(from, DAYS - 1);
  const query = useCalendar(from, to);

  return (
    <>
      <PageHeader
        title="Calendario"
        subtitle={`Dal ${formatDate(from)} al ${formatDate(to)}`}
        actions={
          <Link to="/app/schedule" className="btn btn--secondary btn--sm">
            Modifica giorni
          </Link>
        }
      />
      <nav className="pagination" aria-label="Periodo" style={{ marginTop: 0, marginBottom: 'var(--space-4)' }}>
        <Button variant="secondary" size="sm" icon={<ChevronLeft size={18} aria-hidden="true" />} onClick={() => setFrom(addDays(from, -DAYS))}>
          Precedenti
        </Button>
        <Button variant="ghost" size="sm" onClick={() => setFrom(mondayOf(today))}>
          Oggi
        </Button>
        <Button variant="secondary" size="sm" onClick={() => setFrom(addDays(from, DAYS))}>
          Successive
          <ChevronRight size={18} aria-hidden="true" />
        </Button>
      </nav>
      <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
        <ol className="calendar-list" aria-label="Giorni">
          {query.data?.map((day) => <CalendarRow key={day.date} day={day} today={today} />)}
        </ol>
      </QueryState>
    </>
  );
}

function CalendarRow({ day, today }: { day: CalendarDay; today: string }) {
  const date = parseLocalDate(day.date);
  const isToday = day.date === today;
  const isPast = day.date < today;
  const classes = ['calendar-day', isToday ? 'calendar-day--today' : '', day.type !== 'TRAINING' ? 'calendar-day--rest' : '']
    .filter(Boolean)
    .join(' ');
  return (
    <li className={classes} aria-current={isToday ? 'date' : undefined}>
      <div className="calendar-day__date">
        <span className="calendar-day__weekday">{weekdayFormatter.format(date)}</span>
        <span className="calendar-day__num">{date.getDate()}</span>
        <span className="calendar-day__weekday">{monthFormatter.format(date)}</span>
      </div>
      <div className="row row--between">
        <div>
          <strong>{day.workout ? day.workout.sessionTitle : day.type === 'TRAINING' ? day.sessionTitle : day.type === 'REST' ? 'Riposo' : '—'}</strong>
          {isToday ? <span className="muted small"> · oggi</span> : null}
        </div>
        {day.workout ? (
          <Link to={day.workout.status === 'IN_PROGRESS' ? `/app/workout/${day.workout.id}` : `/app/history/${day.workout.id}`} aria-label={`Apri allenamento del ${formatDate(day.date)}`}>
            <WorkoutStatusBadge status={day.workout.status} />
          </Link>
        ) : day.type === 'TRAINING' && isPast ? (
          <StatusBadge tone="neutral" icon={<CircleSlash size={14} aria-hidden="true" />}>
            Non svolto
          </StatusBadge>
        ) : null}
      </div>
    </li>
  );
}

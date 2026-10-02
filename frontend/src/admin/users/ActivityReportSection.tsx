import { useQuery } from '@tanstack/react-query';
import { http } from '../../shared/api/http';
import { QueryState } from '../../shared/components/States';
import { formatDate, WEEKDAYS } from '../../shared/utils/format';

interface Totals {
  workoutsCompleted: number;
  workoutsInterrupted: number;
  workoutsInProgress: number;
  setsCompleted: number;
  exercisesCompleted: number;
  exercisesSkipped: number;
  firstWorkoutDate: string | null;
  lastWorkoutDate: string | null;
}

interface PlanActivity {
  assignmentId: string;
  planName: string;
  status: 'ACTIVE' | 'PENDING' | 'CLOSED';
  startDate: string;
  endDate: string | null;
  weekdays: number[];
  planExpiresOn: string | null;
  recommendedDurationEnded: boolean;
  workoutsCompleted: number;
  workoutsInterrupted: number;
  workoutsInProgress: number;
  setsCompleted: number;
  exercisesCompleted: number;
  exercisesSkipped: number;
  lastWorkoutDate: string | null;
  sessionsInPlan: number;
  distinctSessionsCompleted: number;
}

interface WeekActivity {
  weekStart: string;
  workoutsCompleted: number;
  workoutsInterrupted: number;
  setsCompleted: number;
}

export interface ActivityReport {
  userId: string;
  generatedOn: string;
  totals: Totals;
  plans: PlanActivity[];
  weeks: WeekActivity[];
}

const STATUS: Record<PlanActivity['status'], string> = { ACTIVE: 'Attiva', PENDING: 'In attesa', CLOSED: 'Chiusa' };

const days = (list: number[]) => (list.length ? list.map((d) => WEEKDAYS[d - 1]?.short ?? '?').join(', ') : '—');

/**
 * ADMIN activity report (ADR 0010): measured numbers in tables, interpretations kept apart and
 * labelled. Set loads/reps belong to the USER progress report; this report covers activity.
 */
export function ActivityReportSection({ userId }: { userId: string }) {
  const query = useQuery({
    queryKey: ['admin', 'users', 'report', userId],
    queryFn: () => http.get<ActivityReport>(`/api/admin/users/${userId}/activity-report`),
  });
  return (
    <section className="card" aria-labelledby="report-title">
      <h2 id="report-title" className="card__title">
        Report attività
      </h2>
      <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()} loadingLabel="Calcolo del report…">
        {query.data ? <ReportBody report={query.data} /> : null}
      </QueryState>
    </section>
  );
}

function ReportBody({ report }: { report: ActivityReport }) {
  const t = report.totals;
  const anyWorkout = t.workoutsCompleted + t.workoutsInterrupted + t.workoutsInProgress > 0;
  const maxSets = Math.max(1, ...report.weeks.map((w) => w.setsCompleted));
  const finished = t.workoutsCompleted + t.workoutsInterrupted;
  const activeWeeks = report.weeks.filter((w) => w.workoutsCompleted + w.workoutsInterrupted > 0).length;

  return (
    <div className="stack">
      <p className="muted small" style={{ margin: 0 }}>
        Dati di attività registrati dall'app al {formatDate(report.generatedOn)}. Questo report riepiloga allenamenti,
        esercizi e serie; non misura progressi fisici.
      </p>

      <h3>Dati registrati</h3>
      {!anyWorkout ? (
        <p>
          <strong>Nessun allenamento registrato.</strong>
        </p>
      ) : (
        <div className="table-wrap" tabIndex={0} role="group" aria-label="Tabella, scorribile in orizzontale">
          <table className="table">
            <caption>Totale allenamenti ed esercizi</caption>
            <tbody>
              <tr>
                <th scope="row">Allenamenti completati</th>
                <td>{t.workoutsCompleted}</td>
              </tr>
              <tr>
                <th scope="row">Allenamenti interrotti</th>
                <td>{t.workoutsInterrupted}</td>
              </tr>
              <tr>
                <th scope="row">Allenamenti in corso</th>
                <td>{t.workoutsInProgress}</td>
              </tr>
              <tr>
                <th scope="row">Serie completate</th>
                <td>{t.setsCompleted}</td>
              </tr>
              <tr>
                <th scope="row">Esercizi completati / saltati</th>
                <td>
                  {t.exercisesCompleted} / {t.exercisesSkipped}
                </td>
              </tr>
              <tr>
                <th scope="row">Primo e ultimo allenamento</th>
                <td>
                  {formatDate(t.firstWorkoutDate)} – {formatDate(t.lastWorkoutDate)}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      )}

      {report.plans.length === 0 ? (
        <p>Nessuna scheda assegnata.</p>
      ) : (
        <div className="table-wrap" tabIndex={0} role="group" aria-label="Tabella, scorribile in orizzontale">
          <table className="table">
            <caption>Schede assegnate e attività per scheda</caption>
            <thead>
              <tr>
                <th scope="col">Scheda</th>
                <th scope="col">Stato</th>
                <th scope="col">Periodo</th>
                <th scope="col">Giorni</th>
                <th scope="col">Completati</th>
                <th scope="col">Interrotti</th>
                <th scope="col">In corso</th>
                <th scope="col">Serie</th>
                <th scope="col">Sessioni svolte</th>
                <th scope="col">Ultimo</th>
              </tr>
            </thead>
            <tbody>
              {report.plans.map((p) => (
                <tr key={p.assignmentId}>
                  <th scope="row">
                    {p.planName}
                    {p.recommendedDurationEnded ? <div className="small">Durata consigliata terminata</div> : null}
                  </th>
                  <td>{STATUS[p.status]}</td>
                  <td>
                    dal {formatDate(p.startDate)}
                    {p.endDate ? ` al ${formatDate(p.endDate)}` : ''}
                  </td>
                  <td>{days(p.weekdays)}</td>
                  <td>{p.workoutsCompleted}</td>
                  <td>{p.workoutsInterrupted}</td>
                  <td>{p.workoutsInProgress}</td>
                  <td>{p.setsCompleted}</td>
                  <td>
                    {p.distinctSessionsCompleted} di {p.sessionsInPlan}
                  </td>
                  <td>{formatDate(p.lastWorkoutDate)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="table-wrap" tabIndex={0} role="group" aria-label="Tabella, scorribile in orizzontale">
        <table className="table">
          <caption>Andamento nelle ultime {report.weeks.length} settimane</caption>
          <thead>
            <tr>
              <th scope="col">Settimana dal</th>
              <th scope="col">Completati</th>
              <th scope="col">Interrotti</th>
              <th scope="col">Serie completate</th>
            </tr>
          </thead>
          <tbody>
            {report.weeks.map((w) => (
              <tr key={w.weekStart}>
                <th scope="row">{formatDate(w.weekStart)}</th>
                <td>{w.workoutsCompleted}</td>
                <td>{w.workoutsInterrupted}</td>
                <td>
                  {/* Bar with the value written next to it: never colour or length alone. */}
                  <span className="bar">
                    <span className="bar__fill" style={{ width: `${(w.setsCompleted / maxSets) * 100}%` }} aria-hidden="true" />
                    <span className="bar__value">{w.setsCompleted}</span>
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <h3>Lettura dei dati</h3>
      <div className="alert alert--info" role="note">
        <div className="alert__body">
          <div className="alert__title">Interpretazione, non misura</div>
          {finished === 0 ? (
            <p>Non ci sono ancora allenamenti conclusi da interpretare.</p>
          ) : (
            <ul style={{ margin: 0 }}>
              <li>
                Allenamenti portati a termine: {Math.round((t.workoutsCompleted / finished) * 100)}% di quelli conclusi (
                {t.workoutsCompleted} su {finished}).
              </li>
              <li>
                Settimane con almeno un allenamento: {activeWeeks} su {report.weeks.length}.
              </li>
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}

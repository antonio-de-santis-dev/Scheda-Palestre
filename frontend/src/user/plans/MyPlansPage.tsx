import { Link } from 'react-router';
import { ClipboardList } from 'lucide-react';
import { PageHeader } from '../../shared/components/PageHeader';
import { EmptyState, QueryState } from '../../shared/components/States';
import { formatDate, WEEKDAYS } from '../../shared/utils/format';
import { AssignmentStatusBadge } from '../../admin/assignments/AssignmentStatusBadge';
import { useSchedules } from '../schedule/api';
import { useMyAssignments, type MyAssignment } from './api';

export function weekdaysText(days: number[] | undefined): string {
  if (!days || days.length === 0) {
    return 'Giorni non ancora scelti';
  }
  return `Giorni: ${days.map((d) => WEEKDAYS[d - 1]?.short ?? '?').join(', ')}`;
}

/** Active plans first (a USER can have several), then pending and closed ones. */
export function MyPlansPage() {
  const query = useMyAssignments();
  const schedules = useSchedules();
  const all = query.data ?? [];
  const active = all.filter((a) => a.status === 'ACTIVE');
  const others = all.filter((a) => a.status !== 'ACTIVE');
  const daysOf = (id: string) => schedules.data?.find((s) => s.assignmentId === id)?.weekdays;

  return (
    <>
      <PageHeader title="Le mie schede" subtitle="Schede ricevute dalla palestra, in sola lettura." />
      <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
        {all.length === 0 ? (
          <EmptyState title="Nessuna scheda assegnata" icon={<ClipboardList size={40} />}>
            <p>Quando la palestra ti assegnerà una scheda la troverai qui.</p>
          </EmptyState>
        ) : (
          <div className="stack">
            {active.length > 0 ? (
              <section aria-labelledby="active-plans">
                <h2 id="active-plans">{active.length === 1 ? 'Scheda attiva' : `Schede attive (${active.length})`}</h2>
                <PlanCards items={active} daysOf={daysOf} />
              </section>
            ) : null}
            {others.length > 0 ? (
              <section aria-labelledby="other-plans">
                <h2 id="other-plans">Altre schede</h2>
                <PlanCards items={others} />
              </section>
            ) : null}
          </div>
        )}
      </QueryState>
    </>
  );
}

function PlanCards({ items, daysOf }: { items: MyAssignment[]; daysOf?: (id: string) => number[] | undefined }) {
  return (
    <ul className="plan-cards">
      {items.map((a) => (
        <li key={a.id} className="plan-card">
          <div className="plan-card__head">
            <h3 className="plan-card__title">
              {/* The link covers the whole card (::after): one link per destination. */}
              <Link className="plan-card__link" to={`/app/plans/${a.id}`}>
                {a.planName}
              </Link>
            </h3>
            <AssignmentStatusBadge status={a.status} />
          </div>
          <p className="plan-card__meta">
            dal {formatDate(a.startDate)}
            {a.endDate ? ` al ${formatDate(a.endDate)}` : ''}
          </p>
          {daysOf ? <p className="plan-card__meta">{weekdaysText(daysOf(a.id))}</p> : null}
        </li>
      ))}
    </ul>
  );
}

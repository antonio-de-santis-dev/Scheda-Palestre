import { Link, useParams } from 'react-router';
import { CalendarCheck } from 'lucide-react';
import { PageHeader } from '../../shared/components/PageHeader';
import { QueryState } from '../../shared/components/States';
import { PlanSessionView } from '../../shared/components/PlanSessionView';
import { formatDate } from '../../shared/utils/format';
import { AssignmentStatusBadge } from '../../admin/assignments/AssignmentStatusBadge';
import { useSchedules } from '../schedule/api';
import { useMyAssignments, useMyPlan } from './api';
import { weekdaysText } from './MyPlansPage';

/** Read-only plan: sessions > muscle groups > exercises > sets/reps/rest, with real headings. */
export function MyPlanDetailPage() {
  const { assignmentId = '' } = useParams();
  const query = useMyPlan(assignmentId);
  const assignment = useMyAssignments().data?.find((a) => a.id === assignmentId);
  const days = useSchedules().data?.find((s) => s.assignmentId === assignmentId)?.weekdays;
  const plan = query.data;
  return (
    <>
      <PageHeader
        title={plan?.name ?? 'Scheda'}
        subtitle={plan?.expiresOn ? `Durata consigliata fino al ${formatDate(plan.expiresOn)}` : undefined}
        back={{ to: '/app/plans', label: 'Le mie schede' }}
      />
      <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
        {plan ? (
          <div className="stack">
            {assignment ? (
              <div className="row">
                <AssignmentStatusBadge status={assignment.status} />
                {assignment.status === 'ACTIVE' ? (
                  <>
                    <span className="muted small">{weekdaysText(days)}</span>
                    <Link className="btn btn--secondary btn--sm" to={`/app/schedule?assignment=${assignmentId}`}>
                      <CalendarCheck size={16} aria-hidden="true" />
                      Modifica i giorni di {plan.name}
                    </Link>
                  </>
                ) : null}
              </div>
            ) : null}
            {plan.description ? <p>{plan.description}</p> : null}
            {plan.sessions.length === 0 ? <p className="muted">La scheda è in preparazione.</p> : null}
            <div>
              {plan.sessions.map((s) => (
                <PlanSessionView key={s.id} session={s} />
              ))}
            </div>
          </div>
        ) : null}
      </QueryState>
    </>
  );
}

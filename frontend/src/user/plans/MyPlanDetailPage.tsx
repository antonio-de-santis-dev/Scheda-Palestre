import { useState } from 'react';
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
import { RecommendedDurationNotice } from '../../shared/components/RecommendedDurationNotice';
import { SessionTabs } from '../../shared/components/SessionTabs';

/** Read-only plan: sessions > muscle groups > exercises > sets/reps/rest, with real headings. */
export function MyPlanDetailPage() {
  const { assignmentId = '' } = useParams();
  const query = useMyPlan(assignmentId);
  const assignment = useMyAssignments().data?.find((a) => a.id === assignmentId);
  const days = useSchedules().data?.find((s) => s.assignmentId === assignmentId)?.weekdays;
  const plan = query.data;
  const [selectedSession, setSelectedSession] = useState('');
  const activeSession = plan?.sessions.find((s) => s.id === selectedSession) ?? plan?.sessions[0];
  return (
    <>
      <PageHeader
        title={plan?.name ?? 'Scheda'}
        subtitle={assignment?.planExpiresOn ? `Durata consigliata fino al ${formatDate(assignment.planExpiresOn)}` : undefined}
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
            {assignment?.recommendedDurationWarning && assignment.planExpiresOn ? (
              <RecommendedDurationNotice planName={plan.name} expiresOn={assignment.planExpiresOn} ended={assignment.recommendedDurationEnded} />
            ) : null}
            {plan.sessions.length === 0 ? <p className="muted">La scheda è in preparazione.</p> : null}
            {activeSession ? <>
              <SessionTabs sessions={plan.sessions} activeId={activeSession.id} onSelect={setSelectedSession} prefix="my-plan" />
              {plan.sessions.map((session) => <div key={session.id} role="tabpanel" hidden={session.id !== activeSession.id}
                id={`my-plan-panel-${session.id}`} aria-labelledby={`my-plan-tab-${session.id}`} tabIndex={0}>
                <PlanSessionView session={session} />
              </div>)}
            </> : null}
            {plan.description ? <section className="card"><h2>Note della scheda</h2><p className="muted">{plan.description}</p></section> : null}
          </div>
        ) : null}
      </QueryState>
    </>
  );
}

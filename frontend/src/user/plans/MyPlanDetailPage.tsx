import { useParams } from 'react-router';
import { PageHeader } from '../../shared/components/PageHeader';
import { QueryState } from '../../shared/components/States';
import { PlanSessionView } from '../../shared/components/PlanSessionView';
import { formatDate } from '../../shared/utils/format';

import { useMyPlan } from './api';

export function MyPlanDetailPage() {
  const { assignmentId = '' } = useParams();
  const query = useMyPlan(assignmentId);
  const plan = query.data;
  return (
    <>
      <PageHeader
        title={plan?.name ?? 'Scheda'}
        subtitle={plan?.expiresOn ? `Scadenza indicativa: ${formatDate(plan.expiresOn)}` : undefined}
        back={{ to: '/app/plans', label: 'Le mie schede' }}
      />
      <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
        {plan ? (
          <div className="stack">
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

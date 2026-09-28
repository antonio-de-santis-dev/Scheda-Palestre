import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { Ban, Check, Save } from 'lucide-react';
import { PageHeader } from '../../shared/components/PageHeader';
import { Button } from '../../shared/components/Button';
import { Alert, ErrorAlert } from '../../shared/components/Alert';
import { EmptyState, QueryState } from '../../shared/components/States';
import { WEEKDAYS, formatDate } from '../../shared/utils/format';
import { useSaveSchedule, useSchedules, type PlanSchedule } from './api';

/**
 * Days of every active plan (ADR 0008). A weekday already used by another plan is shown, stays
 * reachable with the keyboard and says which plan owns it, but cannot be selected.
 */
export function SchedulePage() {
  const query = useSchedules();
  const [params] = useSearchParams();
  const focus = params.get('assignment');
  const plans = query.data ?? [];
  return (
    <>
      <PageHeader
        title="Giorni di allenamento"
        subtitle="Per ogni scheda scegli i giorni in cui ti alleni: le sessioni verranno proposte a rotazione. Ogni giorno può appartenere a una sola scheda."
      />
      <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
        {query.data && plans.length === 0 ? (
          <EmptyState title="Nessuna scheda attiva">
            <p>Potrai scegliere i giorni quando la palestra ti assegnerà una scheda.</p>
          </EmptyState>
        ) : (
          <div className="stack">
            {plans.map((plan) => (
              <ScheduleForm
                key={plan.assignmentId}
                plan={plan}
                others={plans.filter((p) => p.assignmentId !== plan.assignmentId)}
                focused={focus === plan.assignmentId}
              />
            ))}
          </div>
        )}
      </QueryState>
    </>
  );
}

function ScheduleForm({ plan, others, focused }: { plan: PlanSchedule; others: PlanSchedule[]; focused: boolean }) {
  const [selected, setSelected] = useState<Set<number>>(new Set(plan.weekdays));
  const save = useSaveSchedule(plan.assignmentId);
  const [saved, setSaved] = useState(false);
  // Align with the server when its days change (after a save or a refetch), keeping the message.
  const serverDays = plan.weekdays.join(',');
  const [syncedDays, setSyncedDays] = useState(serverDays);
  if (syncedDays !== serverDays) {
    setSyncedDays(serverDays);
    setSelected(new Set(plan.weekdays));
  }
  const headingRef = useRef<HTMLHeadingElement>(null);
  const changed = selected.size !== plan.weekdays.length || plan.weekdays.some((d) => !selected.has(d));
  const titleId = `schedule-${plan.assignmentId}`;
  const owner = (day: number) => others.find((p) => p.weekdays.includes(day)) ?? null;

  // Arriving from "Scegli i giorni" of a specific plan: bring its form into view.
  useEffect(() => {
    if (focused) {
      headingRef.current?.focus();
    }
  }, [focused]);

  return (
    <section className="card" aria-labelledby={titleId}>
      <h2 id={titleId} className="card__title" ref={headingRef} tabIndex={-1}>
        {plan.planName}
      </h2>
      <p className="muted small" style={{ marginTop: 0 }}>
        Attiva dal {formatDate(plan.startDate)} ·{' '}
        {plan.weekdays.length === 0 ? 'nessun giorno scelto' : `${plan.weekdays.length} ${plan.weekdays.length === 1 ? 'giorno' : 'giorni'} a settimana`}
      </p>
      <form
        className="form"
        onSubmit={(e) => {
          e.preventDefault();
          setSaved(false);
          save.mutate([...selected].sort((a, b) => a - b), { onSuccess: () => setSaved(true) });
        }}
      >
        {save.error ? <ErrorAlert error={save.error} /> : null}
        {saved && !changed ? (
          <Alert tone="success">
            <p>
              Giorni salvati. <Link to="/app/calendar">Guarda il calendario</Link>
            </p>
          </Alert>
        ) : null}
        <fieldset className="weekdays">
          <legend className="field__label" style={{ marginBottom: 'var(--space-2)' }}>
            Giorni di “{plan.planName}” ({selected.size} selezionati)
          </legend>
          {WEEKDAYS.map((day) => {
            const checked = selected.has(day.value);
            const takenBy = checked ? null : owner(day.value);
            const reasonId = `${titleId}-reason-${day.value}`;
            return (
              <label key={day.value} className={`weekday${takenBy ? ' weekday--taken' : ''}`}>
                <input
                  type="checkbox"
                  checked={checked}
                  aria-disabled={takenBy ? true : undefined}
                  aria-describedby={takenBy ? reasonId : undefined}
                  onChange={() => {
                    if (takenBy) {
                      return;
                    }
                    setSaved(false);
                    setSelected((prev) => {
                      const next = new Set(prev);
                      if (next.has(day.value)) {
                        next.delete(day.value);
                      } else {
                        next.add(day.value);
                      }
                      return next;
                    });
                  }}
                />
                <span>
                  <span className="weekday__text">
                    {day.label}
                    {takenBy ? (
                      <span className="weekday__reason" id={reasonId}>
                        Occupato da “{takenBy.planName}”
                      </span>
                    ) : null}
                  </span>
                  {checked ? <Check size={20} aria-hidden="true" /> : takenBy ? <Ban size={20} aria-hidden="true" /> : null}
                </span>
              </label>
            );
          })}
        </fieldset>
        {selected.size === 0 ? (
          <Alert tone="info">
            <p>Senza giorni selezionati non verranno pianificati allenamenti per questa scheda.</p>
          </Alert>
        ) : null}
        <p className="muted small">
          Il cambio vale da oggi in avanti: gli allenamenti già svolti o in corso non cambiano e la rotazione riprende dalla
          sessione prevista.
        </p>
        <div className="form-actions">
          <Button type="submit" size="lg" loading={save.isPending} disabled={!changed} icon={<Save size={20} aria-hidden="true" />}>
            Salva giorni di {plan.planName}
          </Button>
        </div>
      </form>
    </section>
  );
}

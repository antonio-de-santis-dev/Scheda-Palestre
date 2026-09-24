import { useState } from 'react';
import { Link } from 'react-router';
import { Check, Save } from 'lucide-react';
import { PageHeader } from '../../shared/components/PageHeader';
import { Button } from '../../shared/components/Button';
import { Alert, ErrorAlert } from '../../shared/components/Alert';
import { EmptyState, QueryState } from '../../shared/components/States';
import { WEEKDAYS } from '../../shared/utils/format';
import { useSaveSchedule, useSchedule } from './api';

export function SchedulePage() {
  const query = useSchedule();
  return (
    <>
      <PageHeader
        title="Giorni di allenamento"
        subtitle="Scegli i giorni in cui vai in palestra: le sessioni della scheda verranno proposte a rotazione."
      />
      <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
        {query.data && query.data.assignmentId === null ? (
          <EmptyState title="Nessuna scheda attiva">
            <p>Potrai scegliere i giorni quando la palestra ti assegnerà una scheda.</p>
          </EmptyState>
        ) : query.data ? (
          <ScheduleForm key={query.data.assignmentId} initial={query.data.weekdays} />
        ) : null}
      </QueryState>
    </>
  );
}

function ScheduleForm({ initial }: { initial: number[] }) {
  const [selected, setSelected] = useState<Set<number>>(new Set(initial));
  const [saved, setSaved] = useState(false);
  const save = useSaveSchedule();
  const changed = selected.size !== initial.length || initial.some((d) => !selected.has(d));

  return (
    <form
      className="card form"
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
          Giorni della settimana ({selected.size} selezionati)
        </legend>
        {WEEKDAYS.map((day) => {
          const checked = selected.has(day.value);
          return (
            <label key={day.value} className="weekday">
              <input
                type="checkbox"
                checked={checked}
                onChange={() => {
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
                {day.label}
                {checked ? <Check size={20} aria-hidden="true" /> : null}
              </span>
            </label>
          );
        })}
      </fieldset>
      {selected.size === 0 ? (
        <Alert tone="info">
          <p>Senza giorni selezionati non verranno pianificati allenamenti.</p>
        </Alert>
      ) : null}
      <p className="muted small">
        Il cambio vale da oggi in avanti: gli allenamenti già svolti non cambiano e la rotazione riprende dalla sessione prevista.
      </p>
      <div className="form-actions">
        <Button type="submit" size="lg" loading={save.isPending} disabled={!changed} icon={<Save size={20} aria-hidden="true" />}>
          Salva giorni
        </Button>
      </div>
    </form>
  );
}

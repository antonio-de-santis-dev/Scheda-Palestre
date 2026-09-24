import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { Power, Send, XCircle } from 'lucide-react';
import { PageHeader } from '../../shared/components/PageHeader';
import { Button } from '../../shared/components/Button';
import { Alert, ErrorAlert } from '../../shared/components/Alert';
import { Checkbox, TextField } from '../../shared/components/Field';
import { ConfirmDialog } from '../../shared/components/ConfirmDialog';
import { EmptyState, LoadingState, QueryState } from '../../shared/components/States';
import { formatDate, todayIso } from '../../shared/utils/format';
import { useDebouncedValue } from '../../shared/utils/useDebouncedValue';
import { usePlan } from '../plans/api';
import { PlanStatusBadge } from '../plans/PlanStatusBadge';
import { useUsers } from '../users/api';
import { assignmentsApi, useAssignmentMutation, usePlanAssignments, type Assignment } from './api';
import { AssignmentStatusBadge } from './AssignmentStatusBadge';

export function PlanAssignmentsPage() {
  const { id = '' } = useParams();
  const plan = usePlan(id);
  const assignments = usePlanAssignments(id);
  const [search, setSearch] = useState('');
  const q = useDebouncedValue(search.trim(), 300);
  const users = useUsers({ q: q || undefined, role: 'USER', active: true, size: 50 });
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [startDate, setStartDate] = useState(todayIso());
  const [activate, setActivate] = useState(true);
  const [copySchedule, setCopySchedule] = useState(true);
  const [done, setDone] = useState<number | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [toClose, setToClose] = useState<Assignment | null>(null);

  const assign = useAssignmentMutation(assignmentsApi.assign);
  const activateOne = useAssignmentMutation((a: Assignment) => assignmentsApi.activate(a.id, true));
  const close = useAssignmentMutation((a: Assignment) => assignmentsApi.close(a.id));
  const actionError = activateOne.error ?? close.error;

  const toggle = (userId: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(userId)) {
        next.delete(userId);
      } else {
        next.add(userId);
      }
      return next;
    });
  };

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    setDone(null);
    if (selected.size === 0) {
      setFormError('Seleziona almeno un utente');
      return;
    }
    if (!startDate) {
      setFormError('Indica la data di inizio');
      return;
    }
    setFormError(null);
    assign.mutate(
      { planId: id, userIds: [...selected], startDate, activate, copySchedule },
      {
        onSuccess: (result) => {
          setDone(result.length);
          setSelected(new Set());
        },
      },
    );
  };

  const activeCount = assignments.data?.filter((a) => a.status === 'ACTIVE').length ?? 0;

  return (
    <>
      <PageHeader
        title={plan.data ? `Assegnazioni: ${plan.data.name}` : 'Assegnazioni'}
        subtitle={`${activeCount} ${activeCount === 1 ? 'utente attivo' : 'utenti attivi'}`}
        back={{ to: '/admin/plans', label: 'Schede' }}
        actions={
          <Link className="btn btn--secondary" to={`/admin/plans/${id}/edit`}>
            Apri editor
          </Link>
        }
      />
      <div className="stack">
        {plan.data && !plan.data.executable ? (
          <Alert tone="warning" title="Scheda non ancora eseguibile">
            <p>
              Puoi creare assegnazioni <strong>in attesa</strong>, ma per attivarle la scheda deve avere almeno una sessione e un
              esercizio in ogni sessione.
            </p>
          </Alert>
        ) : null}
        {plan.data ? <PlanStatusBadge executable={plan.data.executable} deleted={plan.data.deletedAt !== null} /> : null}

        <section className="card" aria-labelledby="assign-title">
          <h2 id="assign-title" className="card__title">
            Assegna a utenti
          </h2>
          <form className="form" onSubmit={submit} noValidate>
            {assign.error ? <ErrorAlert error={assign.error} /> : null}
            {formError ? <Alert tone="error">{formError}</Alert> : null}
            {done !== null ? (
              <Alert tone="success">
                <p>
                  Scheda assegnata a {done} {done === 1 ? 'utente' : 'utenti'}.
                </p>
              </Alert>
            ) : null}
            <TextField label="Cerca utenti" type="search" value={search} onChange={(e) => setSearch(e.target.value)} />
            <fieldset style={{ border: 'none', padding: 0, margin: 0 }}>
              <legend className="field__label">Utenti ({selected.size} selezionati)</legend>
              {users.isLoading ? (
                <LoadingState />
              ) : users.data && users.data.content.length > 0 ? (
                <ul className="list" style={{ maxHeight: '18rem', overflowY: 'auto' }}>
                  {users.data.content.map((u) => (
                    <li key={u.id} className="list-item" style={{ padding: '0 var(--space-3)' }}>
                      <Checkbox
                        label={`${u.lastName} ${u.firstName} (@${u.username})`}
                        checked={selected.has(u.id)}
                        onChange={() => toggle(u.id)}
                      />
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="muted">Nessun utente attivo trovato.</p>
              )}
            </fieldset>
            <div className="form-grid form-grid--2">
              <TextField label="Data di inizio" type="date" required value={startDate} onChange={(e) => setStartDate(e.target.value)} />
              <div className="stack stack--sm">
                <Checkbox label="Attiva subito" checked={activate} onChange={(e) => setActivate(e.target.checked)} />
                <Checkbox
                  label="Mantieni i giorni di allenamento della scheda precedente"
                  checked={copySchedule}
                  disabled={!activate}
                  onChange={(e) => setCopySchedule(e.target.checked)}
                />
              </div>
            </div>
            {activate ? (
              <p className="muted small">
                Se un utente ha già un'altra scheda attiva, questa verrà chiusa e l'eventuale allenamento in corso interrotto.
              </p>
            ) : null}
            <div className="form-actions">
              <Button type="submit" loading={assign.isPending} icon={<Send size={18} aria-hidden="true" />}>
                Assegna
              </Button>
            </div>
          </form>
        </section>

        <section aria-labelledby="assignees-title">
          <h2 id="assignees-title">Assegnatari</h2>
          {actionError ? <ErrorAlert error={actionError} /> : null}
          <QueryState isLoading={assignments.isLoading} error={assignments.error} onRetry={() => void assignments.refetch()}>
            {assignments.data && assignments.data.length === 0 ? (
              <EmptyState title="Nessuna assegnazione" />
            ) : (
              <ul className="list" aria-label="Assegnatari">
                {assignments.data?.map((a) => (
                  <li key={a.id} className="list-item">
                    <div className="list-item__main">
                      <div className="list-item__title">
                        <Link to={`/admin/users/${a.userId}`}>{a.userFullName}</Link>
                      </div>
                      <div className="list-item__meta">
                        @{a.username} · dal {formatDate(a.startDate)}
                        {a.endDate ? ` al ${formatDate(a.endDate)}` : ''}
                      </div>
                    </div>
                    <AssignmentStatusBadge status={a.status} />
                    <div className="list-item__actions">
                      {a.status === 'PENDING' ? (
                        <Button
                          variant="secondary"
                          size="sm"
                          icon={<Power size={16} aria-hidden="true" />}
                          aria-label={`Attiva per ${a.userFullName}`}
                          loading={activateOne.isPending && activateOne.variables?.id === a.id}
                          onClick={() => activateOne.mutate(a)}
                        >
                          Attiva
                        </Button>
                      ) : null}
                      {a.status !== 'CLOSED' ? (
                        <Button
                          variant="secondary"
                          size="sm"
                          icon={<XCircle size={16} aria-hidden="true" />}
                          aria-label={`Chiudi assegnazione di ${a.userFullName}`}
                          onClick={() => setToClose(a)}
                        >
                          Chiudi
                        </Button>
                      ) : null}
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </QueryState>
        </section>
      </div>

      <ConfirmDialog
        open={toClose !== null}
        title="Chiudere l'assegnazione?"
        confirmLabel="Chiudi assegnazione"
        loading={close.isPending}
        onCancel={() => setToClose(null)}
        onConfirm={() => {
          if (toClose) {
            close.mutate(toClose, { onSettled: () => setToClose(null) });
          }
        }}
      >
        <p>
          {toClose?.userFullName} non vedrà più allenamenti pianificati da questa scheda; un eventuale allenamento in corso verrà
          interrotto. Lo storico resta disponibile.
        </p>
      </ConfirmDialog>
    </>
  );
}

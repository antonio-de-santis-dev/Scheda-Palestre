import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Copy, FilePlus2, Pencil, RotateCcw, Trash2 } from 'lucide-react';
import { PageHeader } from '../../shared/components/PageHeader';
import { Button } from '../../shared/components/Button';
import { SelectField, TextField } from '../../shared/components/Field';
import { ErrorAlert } from '../../shared/components/Alert';
import { ConfirmDialog } from '../../shared/components/ConfirmDialog';
import { EmptyState, QueryState } from '../../shared/components/States';
import { Pagination } from '../../shared/components/Pagination';
import { useDebouncedValue } from '../../shared/utils/useDebouncedValue';
import { formatDate, formatDateTime } from '../../shared/utils/format';
import { plansApi, plansKeys, usePlans, type PlanListItem } from './api';
import { PlanMetadataForm } from './PlanMetadataForm';
import { PlanStatusBadge } from './PlanStatusBadge';

export function PlansPage() {
  const [params, setParams] = useSearchParams();
  const [text, setText] = useState(params.get('q') ?? '');
  const q = useDebouncedValue(text.trim(), 300);
  const deleted = params.get('view') === 'deleted';
  const page = Number(params.get('page') ?? '0') || 0;
  const [creating, setCreating] = useState(false);
  const [toDelete, setToDelete] = useState<PlanListItem | null>(null);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const query = usePlans({ q: q || undefined, deleted, page, size: 20 });
  const invalidate = () => queryClient.invalidateQueries({ queryKey: plansKeys.all });
  const create = useMutation({ mutationFn: plansApi.create, onSuccess: invalidate });
  const duplicate = useMutation({ mutationFn: plansApi.duplicate, onSuccess: invalidate });
  const remove = useMutation({ mutationFn: plansApi.remove, onSuccess: invalidate });
  const restore = useMutation({ mutationFn: plansApi.restore, onSuccess: invalidate });
  const actionError = duplicate.error ?? remove.error ?? restore.error;

  const setParam = (key: string, value: string | null) => {
    const next = new URLSearchParams(params);
    if (value) {
      next.set(key, value);
    } else {
      next.delete(key);
    }
    if (key !== 'page') {
      next.delete('page');
    }
    setParams(next, { replace: true });
  };

  return (
    <>
      <PageHeader
        title="Schede"
        subtitle="Le schede sono condivise: la stessa scheda può essere assegnata a più utenti."
        actions={
          !creating ? (
            <Button icon={<FilePlus2 size={18} aria-hidden="true" />} onClick={() => setCreating(true)}>
              Nuova scheda
            </Button>
          ) : undefined
        }
      />

      {creating ? (
        <section className="card" aria-labelledby="new-plan-title" style={{ marginBottom: 'var(--space-4)' }}>
          <h2 id="new-plan-title" className="card__title">
            Nuova scheda
          </h2>
          <PlanMetadataForm
            submitLabel="Crea e apri l'editor"
            pending={create.isPending}
            error={create.error}
            onSubmit={async (values) => {
              const plan = await create.mutateAsync(values);
              navigate(`/admin/plans/${plan.id}/edit`);
            }}
            onCancel={() => {
              setCreating(false);
              create.reset();
            }}
          />
        </section>
      ) : null}

      <div className="toolbar" role="search">
        <TextField
          label="Cerca"
          type="search"
          value={text}
          onChange={(e) => {
            setText(e.target.value);
            setParam('q', e.target.value.trim() || null);
          }}
        />
        <SelectField label="Mostra" value={deleted ? 'deleted' : 'active'} onChange={(e) => setParam('view', e.target.value === 'deleted' ? 'deleted' : null)}>
          <option value="active">Schede attive</option>
          <option value="deleted">Schede eliminate</option>
        </SelectField>
      </div>

      {actionError ? <ErrorAlert error={actionError} /> : null}
      <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
        {query.data && query.data.content.length === 0 ? (
          <EmptyState title={deleted ? 'Nessuna scheda eliminata' : 'Nessuna scheda'}>
            {!deleted ? <p>Crea la prima scheda con il pulsante “Nuova scheda”.</p> : null}
          </EmptyState>
        ) : (
          <>
            <ul className="list" aria-label="Elenco schede">
              {query.data?.content.map((plan) => (
                <li key={plan.id} className="list-item">
                  <div className="list-item__main">
                    <div className="list-item__title">{plan.name}</div>
                    <div className="list-item__meta">
                      {plan.sessionCount} {plan.sessionCount === 1 ? 'sessione' : 'sessioni'}
                      {plan.expiresOn ? ` · scade il ${formatDate(plan.expiresOn)}` : ''} · modificata il{' '}
                      {formatDateTime(plan.updatedAt)}
                    </div>
                  </div>
                  <PlanStatusBadge executable={plan.executable} deleted={plan.deletedAt !== null} />
                  <div className="list-item__actions">
                    {plan.deletedAt === null ? (
                      <>
                        <Link className="btn btn--secondary btn--sm" to={`/admin/plans/${plan.id}/edit`} aria-label={`Modifica ${plan.name}`}>
                          <Pencil size={16} aria-hidden="true" />
                          Modifica
                        </Link>
                        <Button
                          variant="secondary"
                          size="sm"
                          icon={<Copy size={16} aria-hidden="true" />}
                          aria-label={`Duplica ${plan.name}`}
                          loading={duplicate.isPending && duplicate.variables === plan.id}
                          onClick={() =>
                            duplicate.mutate(plan.id, { onSuccess: (copy) => navigate(`/admin/plans/${copy.id}/edit`) })
                          }
                        >
                          Duplica
                        </Button>
                        <Button
                          variant="secondary"
                          size="sm"
                          icon={<Trash2 size={16} aria-hidden="true" />}
                          aria-label={`Elimina ${plan.name}`}
                          onClick={() => setToDelete(plan)}
                        >
                          Elimina
                        </Button>
                      </>
                    ) : (
                      <Button
                        variant="secondary"
                        size="sm"
                        icon={<RotateCcw size={16} aria-hidden="true" />}
                        aria-label={`Ripristina ${plan.name}`}
                        loading={restore.isPending && restore.variables === plan.id}
                        onClick={() => restore.mutate(plan.id)}
                      >
                        Ripristina
                      </Button>
                    )}
                  </div>
                </li>
              ))}
            </ul>
            <Pagination
              page={query.data?.page ?? 0}
              totalPages={query.data?.totalPages ?? 0}
              onChange={(p) => setParam('page', String(p))}
            />
          </>
        )}
      </QueryState>

      <ConfirmDialog
        open={toDelete !== null}
        title={`Eliminare “${toDelete?.name ?? ''}”?`}
        confirmLabel="Elimina scheda"
        loading={remove.isPending}
        onCancel={() => setToDelete(null)}
        onConfirm={() => {
          if (toDelete) {
            remove.mutate(toDelete.id, { onSettled: () => setToDelete(null) });
          }
        }}
      >
        <p>
          La scheda verrà spostata tra le eliminate e le sue <strong>assegnazioni attive verranno chiuse</strong>. Lo
          storico degli allenamenti resta invariato. Potrai ripristinarla in seguito.
        </p>
      </ConfirmDialog>
    </>
  );
}

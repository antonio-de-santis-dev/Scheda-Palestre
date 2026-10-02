import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router';
import { KeyRound, Power, PowerOff, Trash2 } from 'lucide-react';
import { PageHeader } from '../../shared/components/PageHeader';
import { Button } from '../../shared/components/Button';
import { Alert, ErrorAlert } from '../../shared/components/Alert';
import { ConfirmDialog } from '../../shared/components/ConfirmDialog';
import { QueryState } from '../../shared/components/States';
import { formatDateTime } from '../../shared/utils/format';
import { useCurrentUser } from '../../auth/useAuth';
import { usersApi, useUser, useUserMutation, type UserWithPassword } from './api';
import { UserForm } from './UserForm';
import { TemporaryPasswordNotice } from './TemporaryPasswordNotice';
import { UserStatusBadges } from './UserStatusBadges';
import { UserAssignmentsSection } from '../assignments/UserAssignmentsSection';
import { DeleteUserDialog } from './DeleteUserDialog';
import { ActivityReportSection } from './ActivityReportSection';
import { flashState } from '../../shared/flash/flash';

type Pending = 'deactivate' | 'reset' | 'delete' | null;

export function UserDetailPage() {
  const { id = '' } = useParams();
  const { data: me } = useCurrentUser();
  const query = useUser(id);
  const update = useUserMutation((input: Parameters<typeof usersApi.update>[1]) => usersApi.update(id, input));
  const activate = useUserMutation(() => usersApi.activate(id));
  const deactivate = useUserMutation(() => usersApi.deactivate(id));
  const reset = useUserMutation(() => usersApi.resetPassword(id));
  const remove = useMutation({ mutationFn: () => usersApi.remove(id) });
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [confirm, setConfirm] = useState<Pending>(null);
  const [saved, setSaved] = useState(false);
  const [temporary, setTemporary] = useState<UserWithPassword | null>(null);
  const user = query.data;
  const isSelf = me?.id === id;
  const actionError = activate.error ?? deactivate.error ?? reset.error;

  return (
    <>
      <PageHeader
        title={user ? `${user.firstName} ${user.lastName}` : 'Utente'}
        subtitle={user ? `@${user.username}` : undefined}
        back={{ to: '/admin/users', label: 'Utenti' }}
      />
      <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
        {user ? (
          <div className="user-detail-layout">
            {temporary ? (
              <TemporaryPasswordNotice
                username={temporary.user.username}
                password={temporary.temporaryPassword}
                onDismiss={() => setTemporary(null)}
              />
            ) : null}
            {actionError ? <ErrorAlert error={actionError} /> : null}
            {user.deleted ? (
              <Alert tone="info" title="Account eliminato">
                <p>I dati personali sono stati anonimizzati; lo storico resta disponibile in forma anonima.</p>
              </Alert>
            ) : null}

            {user.deleted ? null : (
            <section className="card" aria-labelledby="status-title">
              <h2 id="status-title" className="card__title">
                Stato account
              </h2>
              <UserStatusBadges user={user} />
              <p className="muted small" style={{ marginTop: 'var(--space-2)' }}>
                Creato il {formatDateTime(user.createdAt)} · ultima modifica {formatDateTime(user.updatedAt)}
              </p>
              <div className="row">
                {user.active ? (
                  <Button
                    variant="secondary"
                    icon={<PowerOff size={18} aria-hidden="true" />}
                    disabled={isSelf}
                    title={isSelf ? 'Non puoi disattivare il tuo account' : undefined}
                    onClick={() => setConfirm('deactivate')}
                  >
                    Disattiva
                  </Button>
                ) : (
                  <Button
                    variant="secondary"
                    icon={<Power size={18} aria-hidden="true" />}
                    loading={activate.isPending}
                    onClick={() => activate.mutate(undefined)}
                  >
                    Riattiva
                  </Button>
                )}
                <Button variant="secondary" icon={<KeyRound size={18} aria-hidden="true" />} onClick={() => setConfirm('reset')}>
                  Reset password
                </Button>
              </div>
            </section>
            )}

            {user.deleted ? null : (
            <section className="card" aria-labelledby="data-title">
              <h2 id="data-title" className="card__title">
                Dati anagrafici
              </h2>
              {saved ? (
                <div style={{ marginBottom: 'var(--space-3)' }}>
                  <Alert tone="success">
                    <p>Modifiche salvate.</p>
                  </Alert>
                </div>
              ) : null}
              <UserForm
                key={user.updatedAt}
                initial={user}
                submitLabel="Salva modifiche"
                pending={update.isPending}
                error={update.error}
                onSubmit={async (input) => {
                  setSaved(false);
                  await update.mutateAsync(input);
                  setSaved(true);
                }}
              />
            </section>
            )}

            {user.role === 'USER' ? <UserAssignmentsSection userId={user.id} /> : null}
            {user.role === 'USER' ? <ActivityReportSection userId={user.id} /> : null}

            {user.deleted ? null : (
              <section className="card danger-zone" aria-labelledby="danger-title">
                <h2 id="danger-title" className="card__title">
                  Eliminazione account
                </h2>
                {isSelf ? (
                  <p>Non puoi eliminare il tuo account.</p>
                ) : user.protectedAccount ? (
                  <p>Questo è l'account ADMIN iniziale della palestra e non può essere eliminato.</p>
                ) : (
                  <>
                    <p>
                      Revoca gli accessi, chiude le schede assegnate e anonimizza i dati personali. Lo storico degli
                      allenamenti resta conservato in forma anonima. Non si può annullare.
                    </p>
                    <Button
                      variant="danger"
                      icon={<Trash2 size={18} aria-hidden="true" />}
                      onClick={() => {
                        remove.reset();
                        setConfirm('delete');
                      }}
                    >
                      Elimina account di {user.firstName} {user.lastName}
                    </Button>
                  </>
                )}
              </section>
            )}
          </div>
        ) : null}
      </QueryState>

      <DeleteUserDialog
        open={confirm === 'delete'}
        username={user?.username ?? ''}
        pending={remove.isPending}
        error={remove.error}
        onCancel={() => setConfirm(null)}
        onConfirm={() =>
          remove.mutate(undefined, {
            onSuccess: () => {
              void queryClient.invalidateQueries({ queryKey: ['admin'] });
              void navigate('/admin/users', {
                replace: true,
                state: flashState({
                  tone: 'success',
                  title: 'Account eliminato',
                  message: `L'account @${user?.username ?? ''} è stato eliminato e i suoi dati personali anonimizzati.`,
                }),
              });
            },
          })
        }
      />
      <ConfirmDialog
        open={confirm === 'deactivate'}
        title="Disattivare l'account?"
        confirmLabel="Disattiva"
        loading={deactivate.isPending}
        onCancel={() => setConfirm(null)}
        onConfirm={() => deactivate.mutate(undefined, { onSettled: () => setConfirm(null) })}
      >
        <p>L'utente verrà disconnesso subito e non potrà più accedere finché non lo riattivi.</p>
      </ConfirmDialog>
      <ConfirmDialog
        open={confirm === 'reset'}
        title="Generare una nuova password temporanea?"
        confirmLabel="Genera password"
        tone="primary"
        loading={reset.isPending}
        onCancel={() => setConfirm(null)}
        onConfirm={() =>
          reset.mutate(undefined, {
            onSuccess: (result) => setTemporary(result),
            onSettled: () => setConfirm(null),
          })
        }
      >
        <p>La password attuale smetterà di funzionare e le sessioni aperte verranno chiuse.</p>
      </ConfirmDialog>
    </>
  );
}

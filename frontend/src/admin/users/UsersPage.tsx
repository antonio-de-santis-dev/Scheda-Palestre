import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { UserPlus } from 'lucide-react';
import { PageHeader } from '../../shared/components/PageHeader';
import { Button } from '../../shared/components/Button';
import { SelectField, TextField } from '../../shared/components/Field';
import { EmptyState, QueryState } from '../../shared/components/States';
import { Pagination } from '../../shared/components/Pagination';
import { useDebouncedValue } from '../../shared/utils/useDebouncedValue';
import { usersApi, useUserMutation, useUsers, type UserWithPassword } from './api';
import { UserForm } from './UserForm';
import { TemporaryPasswordNotice } from './TemporaryPasswordNotice';
import { UserStatusBadges } from './UserStatusBadges';

const PAGE_SIZE = 20;

export function UsersPage() {
  const [params, setParams] = useSearchParams();
  const [text, setText] = useState(params.get('q') ?? '');
  const q = useDebouncedValue(text.trim(), 300);
  const status = params.get('status') ?? 'all';
  const page = Number(params.get('page') ?? '0') || 0;
  const [creating, setCreating] = useState(false);
  const [created, setCreated] = useState<UserWithPassword | null>(null);

  const query = useUsers({
    q: q || undefined,
    active: status === 'all' ? undefined : status === 'active',
    page,
    size: PAGE_SIZE,
  });
  const create = useUserMutation(usersApi.create);

  const update = (next: Record<string, string | null>) => {
    const merged = new URLSearchParams(params);
    for (const [key, value] of Object.entries(next)) {
      if (value === null || value === '') {
        merged.delete(key);
      } else {
        merged.set(key, value);
      }
    }
    setParams(merged, { replace: true });
  };

  return (
    <>
      <PageHeader
        title="Utenti"
        subtitle="Account USER della palestra. La registrazione pubblica non esiste: gli account li crei tu."
        actions={
          !creating ? (
            <Button icon={<UserPlus size={18} aria-hidden="true" />} onClick={() => setCreating(true)}>
              Nuovo utente
            </Button>
          ) : undefined
        }
      />

      {created ? (
        <div style={{ marginBottom: 'var(--space-4)' }}>
          <TemporaryPasswordNotice
            username={created.user.username}
            password={created.temporaryPassword}
            onDismiss={() => setCreated(null)}
          />
        </div>
      ) : null}

      {creating ? (
        <section className="card" aria-labelledby="new-user-title" style={{ marginBottom: 'var(--space-4)' }}>
          <h2 id="new-user-title" className="card__title">
            Nuovo utente
          </h2>
          <p className="muted small">Il ruolo è sempre USER. Verrà generata una password temporanea.</p>
          <UserForm
            submitLabel="Crea utente"
            pending={create.isPending}
            error={create.error}
            onSubmit={async (input) => {
              const result = await create.mutateAsync(input);
              setCreated(result);
              setCreating(false);
              create.reset();
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
          placeholder="Nome, username o email"
          value={text}
          onChange={(e) => {
            setText(e.target.value);
            update({ q: e.target.value.trim() || null, page: null });
          }}
        />
        <SelectField label="Stato" value={status} onChange={(e) => update({ status: e.target.value, page: null })}>
          <option value="all">Tutti</option>
          <option value="active">Attivi</option>
          <option value="inactive">Disattivati</option>
        </SelectField>
      </div>

      <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
        {query.data && query.data.content.length === 0 ? (
          <EmptyState title="Nessun utente trovato">
            <p>Modifica la ricerca oppure crea un nuovo utente.</p>
          </EmptyState>
        ) : (
          <>
            <p className="muted small" aria-live="polite">
              {query.data?.totalElements ?? 0} utenti
            </p>
            <ul className="list" aria-label="Elenco utenti">
              {query.data?.content.map((user) => (
                <li key={user.id} className="list-item">
                  <div className="list-item__main">
                    <div className="list-item__title">
                      {user.lastName} {user.firstName}
                    </div>
                    <div className="list-item__meta">
                      @{user.username} · {user.email}
                    </div>
                  </div>
                  <UserStatusBadges user={user} />
                  <div className="list-item__actions">
                    <Link
                      className="btn btn--secondary btn--sm"
                      to={`/admin/users/${user.id}`}
                      aria-label={`Apri ${user.firstName} ${user.lastName}`}
                    >
                      Apri
                    </Link>
                  </div>
                </li>
              ))}
            </ul>
            <Pagination
              page={query.data?.page ?? 0}
              totalPages={query.data?.totalPages ?? 0}
              onChange={(p) => update({ page: String(p) })}
            />
          </>
        )}
      </QueryState>
    </>
  );
}

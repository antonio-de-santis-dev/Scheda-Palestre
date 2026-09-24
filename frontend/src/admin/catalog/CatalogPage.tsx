import { useState } from 'react';
import { useSearchParams } from 'react-router';
import { CheckCircle2, Pencil, Power, PowerOff, XCircle } from 'lucide-react';
import { PageHeader } from '../../shared/components/PageHeader';
import { Button } from '../../shared/components/Button';
import { SelectField, TextField } from '../../shared/components/Field';
import { ErrorAlert } from '../../shared/components/Alert';
import { EmptyState, QueryState } from '../../shared/components/States';
import { Pagination } from '../../shared/components/Pagination';
import { StatusBadge } from '../../shared/components/StatusBadge';
import { useDebouncedValue } from '../../shared/utils/useDebouncedValue';
import { catalogApi, useCatalog, useCatalogMutation, type CatalogItem, type CatalogKind } from './api';
import { CatalogNameForm } from './CatalogNameForm';

const TEXT: Record<CatalogKind, { title: string; singular: string; subtitle: string; empty: string }> = {
  'muscle-groups': {
    title: 'Gruppi muscolari',
    singular: 'gruppo muscolare',
    subtitle: 'Usati per suddividere le sessioni delle schede in sezioni.',
    empty: 'Nessun gruppo muscolare trovato',
  },
  exercises: {
    title: 'Esercizi',
    singular: 'esercizio',
    subtitle: 'Catalogo degli esercizi da inserire nelle schede.',
    empty: 'Nessun esercizio trovato',
  },
};

export function CatalogPage({ kind }: { kind: CatalogKind }) {
  const text = TEXT[kind];
  const [params, setParams] = useSearchParams();
  const [search, setSearch] = useState(params.get('q') ?? '');
  const q = useDebouncedValue(search.trim(), 300);
  const status = params.get('status') ?? 'all';
  const page = Number(params.get('page') ?? '0') || 0;
  const [editing, setEditing] = useState<string | null>(null);

  const query = useCatalog(kind, {
    q: q || undefined,
    active: status === 'all' ? undefined : status === 'active',
    page,
    size: 25,
  });
  const create = useCatalogMutation(kind, (name: string) => catalogApi.create(kind, name));
  const rename = useCatalogMutation(kind, ({ id, name }: { id: string; name: string }) => catalogApi.rename(kind, id, name));
  const toggle = useCatalogMutation(kind, ({ id, active }: { id: string; active: boolean }) =>
    catalogApi.setActive(kind, id, active),
  );

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

  const renderItem = (item: CatalogItem) => (
    <li key={item.id} className="list-item">
      {editing === item.id ? (
        <div style={{ flex: 1 }}>
          <CatalogNameForm
            label={`Nuovo nome per ${item.name}`}
            initialName={item.name}
            submitLabel="Salva"
            pending={rename.isPending}
            error={rename.error}
            onSubmit={async (name) => {
              await rename.mutateAsync({ id: item.id, name });
              setEditing(null);
            }}
            onCancel={() => {
              setEditing(null);
              rename.reset();
            }}
          />
        </div>
      ) : (
        <>
          <div className="list-item__main">
            <div className="list-item__title">{item.name}</div>
          </div>
          {item.active ? (
            <StatusBadge tone="success" icon={<CheckCircle2 size={14} aria-hidden="true" />}>
              Attivo
            </StatusBadge>
          ) : (
            <StatusBadge tone="neutral" icon={<XCircle size={14} aria-hidden="true" />}>
              Disattivato
            </StatusBadge>
          )}
          <div className="list-item__actions">
            <Button
              variant="secondary"
              size="sm"
              icon={<Pencil size={16} aria-hidden="true" />}
              aria-label={`Modifica ${item.name}`}
              onClick={() => {
                rename.reset();
                setEditing(item.id);
              }}
            >
              Modifica
            </Button>
            <Button
              variant="secondary"
              size="sm"
              icon={item.active ? <PowerOff size={16} aria-hidden="true" /> : <Power size={16} aria-hidden="true" />}
              loading={toggle.isPending && toggle.variables?.id === item.id}
              aria-label={`${item.active ? 'Disattiva' : 'Riattiva'} ${item.name}`}
              onClick={() => toggle.mutate({ id: item.id, active: !item.active })}
            >
              {item.active ? 'Disattiva' : 'Riattiva'}
            </Button>
          </div>
        </>
      )}
    </li>
  );

  return (
    <>
      <PageHeader title={text.title} subtitle={text.subtitle} />
      <section className="card" style={{ marginBottom: 'var(--space-4)' }}>
        <CatalogNameForm
          label={`Nuovo ${text.singular}`}
          submitLabel="Aggiungi"
          pending={create.isPending}
          error={create.error}
          resetOnSuccess
          onSubmit={(name) => create.mutateAsync(name)}
        />
      </section>

      <div className="toolbar" role="search">
        <TextField
          label="Cerca"
          type="search"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setParam('q', e.target.value.trim() || null);
          }}
        />
        <SelectField label="Stato" value={status} onChange={(e) => setParam('status', e.target.value)}>
          <option value="all">Tutti</option>
          <option value="active">Attivi</option>
          <option value="inactive">Disattivati</option>
        </SelectField>
      </div>

      {toggle.error ? <ErrorAlert error={toggle.error} /> : null}
      <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
        {query.data && query.data.content.length === 0 ? (
          <EmptyState title={text.empty} />
        ) : (
          <>
            <ul className="list" aria-label={`Elenco ${text.title.toLowerCase()}`}>
              {query.data?.content.map(renderItem)}
            </ul>
            <Pagination
              page={query.data?.page ?? 0}
              totalPages={query.data?.totalPages ?? 0}
              onChange={(p) => setParam('page', String(p))}
            />
          </>
        )}
      </QueryState>
    </>
  );
}

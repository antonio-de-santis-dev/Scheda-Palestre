import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import {
    CheckCircle2,
    ChevronLeft,
    ChevronRight,
    Pencil,
    Power,
    PowerOff,
    XCircle,
} from 'lucide-react';
import { PageHeader } from '../../shared/components/PageHeader';
import { Button } from '../../shared/components/Button';
import { Checkbox, SelectField, TextField } from '../../shared/components/Field';
import { Alert, ErrorAlert } from '../../shared/components/Alert';
import { EmptyState, QueryState } from '../../shared/components/States';
import { Pagination } from '../../shared/components/Pagination';
import { StatusBadge } from '../../shared/components/StatusBadge';
import { Combobox, type ComboboxOption } from '../../shared/components/Combobox';
import { useDebouncedValue } from '../../shared/utils/useDebouncedValue';
import {
    catalogApi,
    useActiveMuscleGroups,
    useCatalogMutation,
    useExercises,
    useMuscleGroup,
    useMuscleGroups,
    type Exercise,
    type MuscleGroup,
} from './api';
import { CatalogNameForm } from './CatalogNameForm';
import { CatalogDeleteButton } from './CatalogDeleteButton';

type StatusFilter = 'all' | 'active' | 'inactive';

const activeParam = (status: StatusFilter) =>
    status === 'all' ? undefined : status === 'active';

function ActiveBadge({ active }: { active: boolean }) {
    return active ? (
        <StatusBadge
            tone="success"
            icon={<CheckCircle2 size={14} aria-hidden="true" />}
        >
            Attivo
        </StatusBadge>
    ) : (
        <StatusBadge
            tone="neutral"
            icon={<XCircle size={14} aria-hidden="true" />}
        >
            Disattivato
        </StatusBadge>
    );
}

const exercisesLabel = (n: number) =>
    `${n} ${n === 1 ? 'esercizio' : 'esercizi'}`;

const countsLabel = (group: MuscleGroup) =>
    `${group.activeExerciseCount} ${
        group.activeExerciseCount === 1 ? 'attivo' : 'attivi'
    } su ${exercisesLabel(group.exerciseCount)}`;

export function CatalogPage() {
    const [params, setParams] = useSearchParams();
    const groupId = params.get('group');

    const select = (id: string | null) => {
        const next = new URLSearchParams(params);

        if (id) {
            next.set('group', id);
        } else {
            next.delete('group');
        }

        next.delete('epage');
        setParams(next);
    };

    return (
        <>
            <PageHeader
                title="Catalogo esercizi"
                subtitle="Ogni esercizio appartiene a un solo gruppo muscolare: nelle schede si sceglie prima il gruppo, poi i suoi esercizi."
            />

            <div className={`catalog${groupId ? ' catalog--detail' : ''}`}>
                <GroupsPanel selectedId={groupId} />

                {groupId ? (
                    <GroupDetail
                        key={groupId}
                        groupId={groupId}
                        onBack={() => select(null)}
                    />
                ) : (
                    <div className="catalog__placeholder">
                        <EmptyState title="Seleziona un gruppo muscolare">
                            <p>
                                Scegli un gruppo dall'elenco per vederne, aggiungerne e
                                modificarne gli esercizi.
                            </p>
                        </EmptyState>
                    </div>
                )}
            </div>
        </>
    );
}

function GroupsPanel({ selectedId }: { selectedId: string | null }) {
    const [params, setParams] = useSearchParams();
    const text = params.get('q') ?? '';
    const q = useDebouncedValue(text.trim(), 300);
    const status = (params.get('status') ?? 'all') as StatusFilter;
    const page = Number(params.get('page') ?? '0') || 0;

    const query = useMuscleGroups({
        q: q || undefined,
        active: activeParam(status),
        page,
        size: 50,
    });

    const create = useCatalogMutation((name: string) =>
        catalogApi.createGroup(name),
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

    const linkTo = (id: string) => {
        const next = new URLSearchParams(params);
        next.set('group', id);
        next.delete('epage');
        return `?${next.toString()}`;
    };

    const total = query.data?.totalElements ?? 0;

    return (
        <section
            className="catalog__groups card"
            aria-labelledby="groups-title"
        >
            <h2 id="groups-title" className="card__title">
                Gruppi muscolari
            </h2>

            <CatalogNameForm
                label="Nuovo gruppo muscolare"
                submitLabel="Aggiungi gruppo"
                pending={create.isPending}
                error={create.error}
                resetOnSuccess
                onSubmit={(name) => create.mutateAsync(name)}
            />

            <div
                className="toolbar"
                role="search"
                aria-label="Filtra gruppi muscolari"
                style={{ marginTop: 'var(--space-4)' }}
            >
                <TextField
                    label="Cerca gruppo"
                    type="search"
                    value={text}
                    onChange={(event) => {
                        setParam('q', event.target.value || null);
                    }}
                />

                <SelectField
                    label="Stato dei gruppi"
                    value={status}
                    onChange={(event) => setParam('status', event.target.value)}
                >
                    <option value="all">Tutti</option>
                    <option value="active">Attivi</option>
                    <option value="inactive">Disattivati</option>
                </SelectField>
            </div>

            <p className="visually-hidden" aria-live="polite">
                {query.data
                    ? `${total} ${total === 1 ? 'gruppo trovato' : 'gruppi trovati'}`
                    : ''}
            </p>

            <QueryState
                isLoading={query.isLoading}
                error={query.error}
                onRetry={() => void query.refetch()}
            >
                {query.data && query.data.content.length === 0 ? (
                    <EmptyState
                        title={
                            q || status !== 'all'
                                ? 'Nessun gruppo trovato'
                                : 'Nessun gruppo muscolare'
                        }
                    >
                        {!q && status === 'all' ? (
                            <p>Crea il primo gruppo con il modulo qui sopra.</p>
                        ) : null}
                    </EmptyState>
                ) : (
                    <>
                        <ul
                            className="list catalog__group-list"
                            aria-label="Elenco gruppi muscolari"
                        >
                            {query.data?.content.map((group) => (
                                <li key={group.id}>
                                    <Link
                                        className="catalog-group"
                                        to={linkTo(group.id)}
                                        aria-current={
                                            group.id === selectedId ? 'true' : undefined
                                        }
                                    >
                    <span className="catalog-group__main">
                      <span className="catalog-group__name">
                        {group.name}
                      </span>
                      <span className="catalog-group__meta">
                        {countsLabel(group)}
                      </span>
                    </span>

                                        {!group.active ? <ActiveBadge active={false} /> : null}
                                        <ChevronRight size={20} aria-hidden="true" />
                                    </Link>
                                </li>
                            ))}
                        </ul>

                        <Pagination
                            page={query.data?.page ?? 0}
                            totalPages={query.data?.totalPages ?? 0}
                            onChange={(newPage) => setParam('page', String(newPage))}
                        />
                    </>
                )}
            </QueryState>
        </section>
    );
}

function GroupDetail({
                         groupId,
                         onBack,
                     }: {
    groupId: string;
    onBack: () => void;
}) {
    const group = useMuscleGroup(groupId);

    return (
        <section
            className="catalog__detail card"
            aria-labelledby="group-detail-title"
        >
            <button
                type="button"
                className="breadcrumb catalog__back"
                onClick={onBack}
            >
                <ChevronLeft size={18} aria-hidden="true" />
                Tutti i gruppi
            </button>

            <QueryState
                isLoading={group.isLoading}
                error={group.error}
                onRetry={() => void group.refetch()}
            >
                {group.data ? (
                    <GroupDetailBody group={group.data} onDeleted={onBack} />
                ) : null}
            </QueryState>
        </section>
    );
}

function GroupDetailBody({
                             group,
                             onDeleted,
                         }: {
    group: MuscleGroup;
    onDeleted: () => void;
}) {
    const [renaming, setRenaming] = useState(false);

    const rename = useCatalogMutation((name: string) =>
        catalogApi.renameGroup(group.id, name),
    );

    const toggle = useCatalogMutation((active: boolean) =>
        catalogApi.setGroupActive(group.id, active),
    );

    return (
        <>
            <div className="row row--between">
                <h2
                    id="group-detail-title"
                    className="card__title"
                    style={{ margin: 0 }}
                >
                    {group.name}
                </h2>
                <ActiveBadge active={group.active} />
            </div>

            <p className="muted small">{countsLabel(group)}.</p>

            {renaming ? (
                <CatalogNameForm
                    label={`Nuovo nome per il gruppo ${group.name}`}
                    initialName={group.name}
                    submitLabel="Salva nome"
                    pending={rename.isPending}
                    error={rename.error}
                    onSubmit={async (name) => {
                        await rename.mutateAsync(name);
                        setRenaming(false);
                    }}
                    onCancel={() => {
                        setRenaming(false);
                        rename.reset();
                    }}
                />
            ) : (
                <div className="row">
                    <Button
                        variant="secondary"
                        size="sm"
                        icon={<Pencil size={16} aria-hidden="true" />}
                        onClick={() => setRenaming(true)}
                    >
                        Rinomina gruppo
                    </Button>

                    <Button
                        variant="secondary"
                        size="sm"
                        loading={toggle.isPending}
                        icon={
                            group.active ? (
                                <PowerOff size={16} aria-hidden="true" />
                            ) : (
                                <Power size={16} aria-hidden="true" />
                            )
                        }
                        onClick={() => toggle.mutate(!group.active)}
                    >
                        {group.active ? 'Disattiva gruppo' : 'Riattiva gruppo'}
                    </Button>

                    <CatalogDeleteButton
                        name={group.name}
                        label="Elimina gruppo"
                        description={`Verranno eliminati il gruppo “${group.name}”, tutti i suoi esercizi e le sezioni collegate nelle schede. Gli esercizi del gruppo saranno rimossi anche dalle altre sezioni che li utilizzano. Gli allenamenti già iniziati e lo storico resteranno disponibili.`}
                        onDelete={() => catalogApi.deleteGroup(group.id)}
                        onDeleted={onDeleted}
                    />
                </div>
            )}

            <p className="muted small">
                Disattivare non elimina: un gruppo disattivato resta nelle schede
                che lo usano ma non si può scegliere per nuove sezioni.
            </p>

            {toggle.error ? <ErrorAlert error={toggle.error} /> : null}

            <ExercisesOfGroup group={group} />
        </>
    );
}

function ExercisesOfGroup({ group }: { group: MuscleGroup }) {
    const [params, setParams] = useSearchParams();
    const [text, setText] = useState('');
    const q = useDebouncedValue(text.trim(), 300);
    const [status, setStatus] = useState<StatusFilter>('all');
    const page = Number(params.get('epage') ?? '0') || 0;

    const query = useExercises({
        muscleGroupId: group.id,
        q: q || undefined,
        active: activeParam(status),
        page,
        size: 25,
    });

    const create = useCatalogMutation((name: string) =>
        catalogApi.createExercise(name, group.id),
    );

    const toggle = useCatalogMutation<
        { id: string; active: boolean },
        Exercise
    >(({ id, active }) => catalogApi.setExerciseActive(id, active));

    const [editing, setEditing] = useState<string | null>(null);
    const [selected, setSelected] = useState<Set<string>>(new Set());
    const [bulkPending, setBulkPending] = useState(false);
    const [bulkResult, setBulkResult] = useState<string | null>(null);
    const [bulkError, setBulkError] = useState<unknown>(null);

    const setPage = (newPage: number) => {
        const next = new URLSearchParams(params);
        next.set('epage', String(newPage));
        setParams(next, { replace: true });
    };

    const toggleSelected = (id: string) =>
        setSelected((previous) => {
            const next = new Set(previous);

            if (next.has(id)) {
                next.delete(id);
            } else {
                next.add(id);
            }

            return next;
        });

    const deactivateSelected = async () => {
        setBulkPending(true);
        setBulkError(null);
        setBulkResult(null);

        let done = 0;

        try {
            for (const id of selected) {
                await toggle.mutateAsync({ id, active: false });
                done++;
            }

            setSelected(new Set());
            setBulkResult(
                `${exercisesLabel(done)} ${
                    done === 1 ? 'disattivato' : 'disattivati'
                }.`,
            );
        } catch (err) {
            setBulkError(err);
        } finally {
            setBulkPending(false);
        }
    };

    const total = query.data?.totalElements ?? 0;
    const items = query.data?.content ?? [];

    return (
        <section
            aria-labelledby="exercises-title"
            className="stack"
            style={{ marginTop: 'var(--space-4)' }}
        >
            <h3 id="exercises-title">Esercizi di {group.name}</h3>

            {group.active ? (
                <CatalogNameForm
                    label={`Nuovo esercizio in ${group.name}`}
                    submitLabel="Aggiungi esercizio"
                    pending={create.isPending}
                    error={create.error}
                    resetOnSuccess
                    onSubmit={(name) => create.mutateAsync(name)}
                />
            ) : (
                <Alert tone="info">
                    <p>
                        Il gruppo è disattivato: riattivalo per aggiungere nuovi esercizi.
                    </p>
                </Alert>
            )}

            <div
                className="toolbar"
                role="search"
                aria-label={`Filtra esercizi di ${group.name}`}
            >
                <TextField
                    label="Cerca esercizio"
                    type="search"
                    value={text}
                    onChange={(event) => { setText(event.target.value); setPage(0); }}
                />

                <SelectField
                    label="Stato degli esercizi"
                    value={status}
                    onChange={(event) =>
                        { setStatus(event.target.value as StatusFilter); setPage(0); }
                    }
                >
                    <option value="all">Tutti</option>
                    <option value="active">Attivi</option>
                    <option value="inactive">Disattivati</option>
                </SelectField>
            </div>

            <p className="visually-hidden" aria-live="polite">
                {query.data
                    ? `${total} ${
                        total === 1 ? 'esercizio trovato' : 'esercizi trovati'
                    }`
                    : ''}
            </p>

            {selected.size > 0 ? (
                <div className="row">
                    <Button
                        variant="secondary"
                        size="sm"
                        loading={bulkPending}
                        icon={<PowerOff size={16} aria-hidden="true" />}
                        onClick={() => void deactivateSelected()}
                    >
                        Disattiva selezionati ({selected.size})
                    </Button>

                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => setSelected(new Set())}
                    >
                        Annulla selezione
                    </Button>
                </div>
            ) : null}

            {bulkResult ? (
                <Alert tone="success">
                    <p>{bulkResult}</p>
                </Alert>
            ) : null}

            {bulkError ? <ErrorAlert error={bulkError} /> : null}

            {toggle.error && !bulkError ? (
                <ErrorAlert error={toggle.error} />
            ) : null}

            <p className="muted small">
                Disattivare un esercizio non lo elimina: resta nelle schede che già
                lo usano e nello storico, ma non si può più aggiungere.
            </p>

            <QueryState
                isLoading={query.isLoading}
                error={query.error}
                onRetry={() => void query.refetch()}
            >
                {items.length === 0 ? (
                    <EmptyState
                        title={
                            q || status !== 'all'
                                ? 'Nessun esercizio trovato in questo gruppo'
                                : 'Nessun esercizio in questo gruppo'
                        }
                    >
                        {group.active && !q && status === 'all' ? (
                            <p>Aggiungi il primo esercizio con il modulo qui sopra.</p>
                        ) : null}
                    </EmptyState>
                ) : (
                    <>
                        <ul
                            className="list"
                            aria-label={`Esercizi di ${group.name}`}
                        >
                            {items.map((exercise) =>
                                    editing === exercise.id ? (
                                        <li key={exercise.id} className="list-item">
                                            <ExerciseForm
                                                exercise={exercise}
                                                onDone={() => setEditing(null)}
                                            />
                                        </li>
                                    ) : (
                                        <li key={exercise.id} className="list-item">
                                            {exercise.active ? (
                                                <Checkbox
                                                    label={
                                                        <span className="visually-hidden">
                            Seleziona {exercise.name}
                          </span>
                                                    }
                                                    checked={selected.has(exercise.id)}
                                                    onChange={() => toggleSelected(exercise.id)}
                                                />
                                            ) : null}

                                            <div className="list-item__main">
                                                <div className="list-item__title">
                                                    {exercise.name}
                                                </div>
                                            </div>

                                            <ActiveBadge active={exercise.active} />

                                            <div className="list-item__actions">
                                                <Button
                                                    variant="secondary"
                                                    size="sm"
                                                    icon={<Pencil size={16} aria-hidden="true" />}
                                                    aria-label={`Modifica ${exercise.name}`}
                                                    onClick={() => setEditing(exercise.id)}
                                                >
                                                    Modifica
                                                </Button>

                                                <Button
                                                    variant="secondary"
                                                    size="sm"
                                                    loading={
                                                        toggle.isPending &&
                                                        toggle.variables?.id === exercise.id &&
                                                        !bulkPending
                                                    }
                                                    icon={
                                                        exercise.active ? (
                                                            <PowerOff size={16} aria-hidden="true" />
                                                        ) : (
                                                            <Power size={16} aria-hidden="true" />
                                                        )
                                                    }
                                                    aria-label={`${
                                                        exercise.active ? 'Disattiva' : 'Riattiva'
                                                    } ${exercise.name}`}
                                                    onClick={() =>
                                                        toggle.mutate({
                                                            id: exercise.id,
                                                            active: !exercise.active,
                                                        })
                                                    }
                                                >
                                                    {exercise.active ? 'Disattiva' : 'Riattiva'}
                                                </Button>

                                                <CatalogDeleteButton
                                                    name={exercise.name}
                                                    label="Elimina"
                                                    description={`L’esercizio “${exercise.name}” verrà eliminato dal catalogo e da tutte le schede che lo utilizzano. Gli allenamenti già iniziati e lo storico resteranno disponibili.`}
                                                    onDelete={() =>
                                                        catalogApi.deleteExercise(exercise.id)
                                                    }
                                                    onDeleted={() => {
                                                        setSelected((previous) => {
                                                            const next = new Set(previous);
                                                            next.delete(exercise.id);
                                                            return next;
                                                        });

                                                        setPage(0);
                                                    }}
                                                />
                                            </div>
                                        </li>
                                    ),
                            )}
                        </ul>

                        <Pagination
                            page={query.data?.page ?? 0}
                            totalPages={query.data?.totalPages ?? 0}
                            onChange={setPage}
                        />
                    </>
                )}
            </QueryState>
        </section>
    );
}

/** Rinomina e/o sposta l'esercizio in un altro gruppo attivo. */
function ExerciseForm({
                          exercise,
                          onDone,
                      }: {
    exercise: Exercise;
    onDone: () => void;
}) {
    const [name, setName] = useState(exercise.name);
    const [groupId, setGroupId] = useState<string | null>(
        exercise.muscleGroupId,
    );
    const [fieldError, setFieldError] = useState<{
        name?: string;
        group?: string;
    }>({});

    const groups = useActiveMuscleGroups();

    const save = useCatalogMutation(() =>
        catalogApi.updateExercise(
            exercise.id,
            name.trim(),
            groupId ?? '',
        ),
    );

    const options: ComboboxOption[] = (groups.data?.content ?? []).map(
        (group) => ({
            id: group.id,
            label: group.name,
        }),
    );

    if (!options.some((option) => option.id === exercise.muscleGroupId)) {
        options.unshift({
            id: exercise.muscleGroupId,
            label: 'Gruppo attuale (disattivato)',
        });
    }

    return (
        <form
            className="form"
            style={{ flex: 1 }}
            noValidate
            aria-label={`Modifica ${exercise.name}`}
            onSubmit={(event) => {
                event.preventDefault();

                const errors: { name?: string; group?: string } = {};

                if (!name.trim()) {
                    errors.name = 'Inserisci un nome';
                } else if (name.trim().length > 100) {
                    errors.name = 'Al massimo 100 caratteri';
                }

                if (!groupId) {
                    errors.group = 'Scegli il gruppo muscolare';
                }

                setFieldError(errors);

                if (!errors.name && !errors.group) {
                    save.mutate(undefined, { onSuccess: onDone });
                }
            }}
        >
            {save.error ? <ErrorAlert error={save.error} /> : null}

            <div className="form-grid form-grid--2">
                <TextField
                    label="Nome esercizio"
                    required
                    value={name}
                    onChange={(event) => setName(event.target.value)}
                    error={fieldError.name}
                />

                <Combobox
                    label="Gruppo muscolare"
                    required
                    options={options}
                    value={groupId}
                    onChange={setGroupId}
                    emptyText="Nessun gruppo trovato"
                    hint="Spostarlo non cambia le schede che già lo usano."
                    error={fieldError.group}
                />
            </div>

            <div className="form-actions">
                <Button
                    variant="secondary"
                    onClick={onDone}
                    disabled={save.isPending}
                >
                    Annulla
                </Button>

                <Button type="submit" loading={save.isPending}>
                    Salva esercizio
                </Button>
            </div>
        </form>
    );
}
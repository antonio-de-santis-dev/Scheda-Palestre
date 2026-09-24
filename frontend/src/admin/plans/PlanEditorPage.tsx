import { useState, type ReactNode } from 'react';
import { useParams } from 'react-router';
import { ArrowDown, ArrowUp, Pencil, Plus, Trash2 } from 'lucide-react';
import { PageHeader } from '../../shared/components/PageHeader';
import { Button } from '../../shared/components/Button';
import { Alert, ErrorAlert } from '../../shared/components/Alert';
import { ConfirmDialog } from '../../shared/components/ConfirmDialog';
import { SelectField, TextField } from '../../shared/components/Field';
import { QueryState } from '../../shared/components/States';
import { StatusBadge } from '../../shared/components/StatusBadge';
import { formatRest } from '../../shared/utils/format';
import { describeSets, type PlanSection, type PlanSession, type PlanStructure } from '../../shared/api/planTypes';
import { useActiveCatalog } from '../catalog/api';
import { moved, plansApi, usePlan, usePlanMutation, type PlanExerciseInput } from './api';
import { PlanMetadataForm } from './PlanMetadataForm';
import { PlanStatusBadge } from './PlanStatusBadge';
import { ExerciseEditor } from './ExerciseEditor';

type Confirm = { title: string; body: ReactNode; label: string; run: () => Promise<unknown> } | null;

export function PlanEditorPage({ assigneesNotice }: { assigneesNotice?: (planId: string) => ReactNode }) {
  const { id = '' } = useParams();
  const query = usePlan(id);
  const plan = query.data;
  const [confirm, setConfirm] = useState<Confirm>(null);
  const [confirmPending, setConfirmPending] = useState(false);
  const [confirmError, setConfirmError] = useState<unknown>(null);
  const [savedMeta, setSavedMeta] = useState(false);

  const updateMeta = usePlanMutation(id, (values: Parameters<typeof plansApi.update>[1]) => plansApi.update(id, values));
  const addSession = usePlanMutation(id, (title: string) => plansApi.addSession(id, title));
  const reorderSessions = usePlanMutation(id, (ids: string[]) => plansApi.reorderSessions(id, ids));
  const structureMutation = usePlanMutation(id, (fn: () => Promise<PlanStructure>) => fn());

  const ask = (c: NonNullable<Confirm>) => {
    setConfirmError(null);
    setConfirm(c);
  };

  return (
    <>
      <PageHeader
        title={plan ? plan.name : 'Scheda'}
        subtitle="Editor della scheda"
        back={{ to: '/admin/plans', label: 'Schede' }}
      />
      <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
        {plan ? (
          <div className="stack">
            {assigneesNotice ? assigneesNotice(plan.id) : null}
            {plan.deletedAt ? (
              <Alert tone="warning" title="Scheda eliminata">
                <p>Ripristinala dall'elenco delle schede per poterla modificare.</p>
              </Alert>
            ) : null}
            <section className="card" aria-labelledby="meta-title">
              <div className="row row--between">
                <h2 id="meta-title" className="card__title">
                  Dati della scheda
                </h2>
                <PlanStatusBadge executable={plan.executable} deleted={plan.deletedAt !== null} />
              </div>
              {!plan.executable && !plan.deletedAt ? (
                <p className="muted small">
                  Per essere assegnata e svolta la scheda deve avere almeno una sessione e almeno un esercizio in ogni
                  sessione.
                </p>
              ) : null}
              {savedMeta ? (
                <div style={{ marginBottom: 'var(--space-3)' }}>
                  <Alert tone="success">
                    <p>Dati salvati.</p>
                  </Alert>
                </div>
              ) : null}
              <PlanMetadataForm
                key={plan.version}
                initial={plan}
                submitLabel="Salva dati"
                disabled={plan.deletedAt !== null}
                pending={updateMeta.isPending}
                error={updateMeta.error}
                onSubmit={async (values) => {
                  setSavedMeta(false);
                  await updateMeta.mutateAsync({ ...values, version: plan.version });
                  setSavedMeta(true);
                }}
              />
            </section>

            {structureMutation.error ? <ErrorAlert error={structureMutation.error} /> : null}
            {reorderSessions.error ? <ErrorAlert error={reorderSessions.error} /> : null}

            <section aria-labelledby="sessions-title" className="stack">
              <h2 id="sessions-title">Sessioni</h2>
              <p className="muted small" style={{ marginTop: 'calc(-1 * var(--space-3))' }}>
                Le sessioni vengono proposte agli utenti a rotazione, in quest'ordine.
              </p>
              {plan.sessions.length === 0 ? <p className="muted">Nessuna sessione. Aggiungi la prima qui sotto.</p> : null}
              <div>
                {plan.sessions.map((session, index) => (
                  <SessionCard
                    key={session.id}
                    session={session}
                    index={index}
                    total={plan.sessions.length}
                    readOnly={plan.deletedAt !== null}
                    onMove={(delta) => reorderSessions.mutate(moved(plan.sessions, index, delta).map((s) => s.id))}
                    run={(fn) => structureMutation.mutateAsync(fn)}
                    ask={ask}
                  />
                ))}
              </div>
              {plan.deletedAt === null ? (
                <AddSessionForm
                  nextNumber={plan.sessions.length + 1}
                  pending={addSession.isPending}
                  error={addSession.error}
                  onAdd={(title) => addSession.mutateAsync(title)}
                />
              ) : null}
            </section>
          </div>
        ) : null}
      </QueryState>

      <ConfirmDialog
        open={confirm !== null}
        title={confirm?.title ?? ''}
        confirmLabel={confirm?.label ?? 'Conferma'}
        loading={confirmPending}
        onCancel={() => setConfirm(null)}
        onConfirm={() => {
          if (!confirm) {
            return;
          }
          setConfirmPending(true);
          confirm
            .run()
            .then(() => setConfirm(null))
            .catch((err: unknown) => setConfirmError(err))
            .finally(() => setConfirmPending(false));
        }}
      >
        {confirm?.body}
        {confirmError ? <ErrorAlert error={confirmError} /> : null}
      </ConfirmDialog>
    </>
  );
}

interface SessionCardProps {
  session: PlanSession;
  index: number;
  total: number;
  readOnly: boolean;
  onMove: (delta: -1 | 1) => void;
  run: (fn: () => Promise<PlanStructure>) => Promise<PlanStructure>;
  ask: (c: NonNullable<Confirm>) => void;
}

function SessionCard({ session, index, total, readOnly, onMove, run, ask }: SessionCardProps) {
  const [renaming, setRenaming] = useState(false);
  const [title, setTitle] = useState(session.title);
  const [renameError, setRenameError] = useState<string | undefined>();
  const groups = useActiveCatalog('muscle-groups');
  const [groupId, setGroupId] = useState('');
  const available = (groups.data?.content ?? []).filter((g) => !session.sections.some((s) => s.muscleGroupId === g.id));
  const titleId = `session-${session.id}`;

  return (
    <article className="plan-session" aria-labelledby={titleId}>
      <header className="plan-session__header">
        {renaming ? (
          <form
            className="row"
            style={{ flex: 1 }}
            onSubmit={(e) => {
              e.preventDefault();
              const value = title.trim();
              if (!value || value.length > 60) {
                setRenameError('Titolo obbligatorio, al massimo 60 caratteri');
                return;
              }
              void run(() => plansApi.renameSession(session.id, value)).then(() => {
                setRenaming(false);
                setRenameError(undefined);
              });
            }}
          >
            <TextField label="Titolo sessione" value={title} onChange={(e) => setTitle(e.target.value)} error={renameError} />
            <Button type="submit" size="sm">
              Salva
            </Button>
            <Button variant="secondary" size="sm" onClick={() => setRenaming(false)}>
              Annulla
            </Button>
          </form>
        ) : (
          <h3 id={titleId}>
            {index + 1}. {session.title}
          </h3>
        )}
        {!readOnly && !renaming ? (
          <div className="row">
            <Button variant="ghost" size="sm" className="icon-btn" aria-label={`Sposta su ${session.title}`} disabled={index === 0} onClick={() => onMove(-1)}>
              <ArrowUp size={18} aria-hidden="true" />
            </Button>
            <Button variant="ghost" size="sm" className="icon-btn" aria-label={`Sposta giù ${session.title}`} disabled={index === total - 1} onClick={() => onMove(1)}>
              <ArrowDown size={18} aria-hidden="true" />
            </Button>
            <Button variant="ghost" size="sm" className="icon-btn" aria-label={`Rinomina ${session.title}`} onClick={() => setRenaming(true)}>
              <Pencil size={18} aria-hidden="true" />
            </Button>
            <Button
              variant="ghost"
              size="sm"
              className="icon-btn"
              aria-label={`Elimina ${session.title}`}
              onClick={() =>
                ask({
                  title: `Eliminare la sessione “${session.title}”?`,
                  body: <p>Verranno eliminate anche le sue sezioni e i suoi esercizi. Gli allenamenti già svolti non cambiano.</p>,
                  label: 'Elimina sessione',
                  run: () => run(() => plansApi.deleteSession(session.id)),
                })
              }
            >
              <Trash2 size={18} aria-hidden="true" />
            </Button>
          </div>
        ) : null}
      </header>
      <div className="plan-session__body">
        {session.sections.length === 0 ? <p className="muted small">Aggiungi un gruppo muscolare per inserire esercizi.</p> : null}
        {session.sections.map((section, sectionIndex) => (
          <SectionBlock
            key={section.id}
            section={section}
            index={sectionIndex}
            total={session.sections.length}
            readOnly={readOnly}
            onMove={(delta) =>
              void run(() => plansApi.reorderSections(session.id, moved(session.sections, sectionIndex, delta).map((s) => s.id)))
            }
            run={run}
            ask={ask}
          />
        ))}
        {!readOnly ? (
          <form
            className="toolbar"
            style={{ marginTop: 'var(--space-4)', marginBottom: 0 }}
            onSubmit={(e) => {
              e.preventDefault();
              if (groupId) {
                void run(() => plansApi.addSection(session.id, groupId)).then(() => setGroupId(''));
              }
            }}
          >
            <SelectField label={`Nuova sezione in ${session.title}`} value={groupId} onChange={(e) => setGroupId(e.target.value)}>
              <option value="">Scegli un gruppo muscolare…</option>
              {available.map((g) => (
                <option key={g.id} value={g.id}>
                  {g.name}
                </option>
              ))}
            </SelectField>
            <Button type="submit" variant="secondary" disabled={!groupId} icon={<Plus size={18} aria-hidden="true" />}>
              Aggiungi sezione
            </Button>
          </form>
        ) : null}
      </div>
    </article>
  );
}

interface SectionBlockProps {
  section: PlanSection;
  index: number;
  total: number;
  readOnly: boolean;
  onMove: (delta: -1 | 1) => void;
  run: (fn: () => Promise<PlanStructure>) => Promise<PlanStructure>;
  ask: (c: NonNullable<Confirm>) => void;
}

function SectionBlock({ section, index, total, readOnly, onMove, run, ask }: SectionBlockProps) {
  const [editing, setEditing] = useState<string | 'new' | null>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const save = async (fn: () => Promise<PlanStructure>) => {
    setPending(true);
    setError(null);
    try {
      await run(fn);
      setEditing(null);
    } catch (err) {
      setError(err);
      throw err;
    } finally {
      setPending(false);
    }
  };

  return (
    <section className="plan-section" aria-label={`Sezione ${section.muscleGroupName}`}>
      <div className="plan-section__title">
        <span>{section.muscleGroupName}</span>
        {!section.muscleGroupActive ? <StatusBadge tone="neutral">Gruppo disattivato</StatusBadge> : null}
        {!readOnly ? (
          <>
            <Button variant="ghost" size="sm" className="icon-btn" aria-label={`Sposta su sezione ${section.muscleGroupName}`} disabled={index === 0} onClick={() => onMove(-1)}>
              <ArrowUp size={18} aria-hidden="true" />
            </Button>
            <Button variant="ghost" size="sm" className="icon-btn" aria-label={`Sposta giù sezione ${section.muscleGroupName}`} disabled={index === total - 1} onClick={() => onMove(1)}>
              <ArrowDown size={18} aria-hidden="true" />
            </Button>
            <Button
              variant="ghost"
              size="sm"
              className="icon-btn"
              aria-label={`Elimina sezione ${section.muscleGroupName}`}
              onClick={() =>
                ask({
                  title: `Eliminare la sezione “${section.muscleGroupName}”?`,
                  body: <p>Verranno eliminati anche gli esercizi della sezione.</p>,
                  label: 'Elimina sezione',
                  run: () => run(() => plansApi.deleteSection(section.id)),
                })
              }
            >
              <Trash2 size={18} aria-hidden="true" />
            </Button>
          </>
        ) : null}
      </div>
      {section.exercises.length === 0 ? <p className="muted small">Nessun esercizio in questa sezione.</p> : null}
      {section.exercises.map((exercise, exerciseIndex) =>
        editing === exercise.id ? (
          <ExerciseEditor
            key={exercise.id}
            initial={exercise}
            pending={pending}
            error={error}
            onSubmit={(input: PlanExerciseInput) => save(() => plansApi.updateExercise(exercise.id, input))}
            onCancel={() => setEditing(null)}
          />
        ) : (
          <div key={exercise.id} className="exercise-row">
            <span className="exercise-row__name">
              {exercise.exerciseName}
              {!exercise.exerciseActive ? (
                <>
                  {' '}
                  <StatusBadge tone="neutral">disattivato</StatusBadge>
                </>
              ) : null}
            </span>
            <span className="exercise-row__values">
              {describeSets(exercise)} · recupero {formatRest(exercise.restSeconds)}
              {exercise.customized ? ' · serie personalizzate' : ''}
            </span>
            {!readOnly ? (
              <span className="row">
                <Button variant="ghost" size="sm" className="icon-btn" aria-label={`Sposta su ${exercise.exerciseName}`} disabled={exerciseIndex === 0}
                  onClick={() => void run(() => plansApi.reorderExercises(section.id, moved(section.exercises, exerciseIndex, -1).map((e) => e.id)))}>
                  <ArrowUp size={18} aria-hidden="true" />
                </Button>
                <Button variant="ghost" size="sm" className="icon-btn" aria-label={`Sposta giù ${exercise.exerciseName}`} disabled={exerciseIndex === section.exercises.length - 1}
                  onClick={() => void run(() => plansApi.reorderExercises(section.id, moved(section.exercises, exerciseIndex, 1).map((e) => e.id)))}>
                  <ArrowDown size={18} aria-hidden="true" />
                </Button>
                <Button variant="ghost" size="sm" className="icon-btn" aria-label={`Modifica ${exercise.exerciseName}`} onClick={() => { setError(null); setEditing(exercise.id); }}>
                  <Pencil size={18} aria-hidden="true" />
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  className="icon-btn"
                  aria-label={`Elimina ${exercise.exerciseName}`}
                  onClick={() =>
                    ask({
                      title: `Eliminare “${exercise.exerciseName}”?`,
                      body: <p>L'esercizio verrà rimosso dalla sezione {section.muscleGroupName}.</p>,
                      label: 'Elimina esercizio',
                      run: () => run(() => plansApi.deleteExercise(exercise.id)),
                    })
                  }
                >
                  <Trash2 size={18} aria-hidden="true" />
                </Button>
              </span>
            ) : null}
          </div>
        ),
      )}
      {!readOnly ? (
        editing === 'new' ? (
          <ExerciseEditor
            pending={pending}
            error={error}
            onSubmit={(input) => save(() => plansApi.addExercise(section.id, input))}
            onCancel={() => setEditing(null)}
          />
        ) : (
          <Button variant="secondary" size="sm" icon={<Plus size={16} aria-hidden="true" />} onClick={() => { setError(null); setEditing('new'); }}>
            Aggiungi esercizio a {section.muscleGroupName}
          </Button>
        )
      ) : null}
    </section>
  );
}

function AddSessionForm({
  nextNumber,
  pending,
  error,
  onAdd,
}: {
  nextNumber: number;
  pending: boolean;
  error: unknown;
  onAdd: (title: string) => Promise<unknown>;
}) {
  const [title, setTitle] = useState('');
  const [fieldError, setFieldError] = useState<string | undefined>();
  const suggested = `Giorno ${nextNumber}`;
  return (
    <form
      className="card"
      onSubmit={(e) => {
        e.preventDefault();
        const value = (title.trim() || suggested).trim();
        if (value.length > 60) {
          setFieldError('Al massimo 60 caratteri');
          return;
        }
        setFieldError(undefined);
        void onAdd(value)
          .then(() => setTitle(''))
          .catch(() => undefined);
      }}
    >
      {error ? <ErrorAlert error={error} /> : null}
      <div className="toolbar" style={{ marginBottom: 0 }}>
        <TextField
          label="Nuova sessione"
          placeholder={suggested}
          hint={`Se vuoto: “${suggested}”`}
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          error={fieldError}
        />
        <Button type="submit" loading={pending} icon={<Plus size={18} aria-hidden="true" />}>
          Aggiungi sessione
        </Button>
      </div>
    </form>
  );
}

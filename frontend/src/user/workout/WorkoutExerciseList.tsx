import { useEffect, useRef, useState, type PointerEvent } from 'react';
import { GripVertical } from 'lucide-react';
import { repsLabel } from '../../shared/api/planTypes';
import { ExerciseStatusBadge } from './ExerciseStatusBadge';
import type { WorkoutState } from './api';

type Drag = { id: string; ids: string[]; pointerId: number | null };

/** Only unfinished exercises participate in sorting; terminal snapshots stay immutable. */
export function WorkoutExerciseList({ state, busy, onSave, onDraggingChange }: {
  state: WorkoutState;
  busy: boolean;
  onSave: (ids: string[]) => void;
  onDraggingChange: (active: boolean) => void;
}) {
  const pending = state.exercises.filter((e) => e.status === 'TODO' || e.status === 'IN_PROGRESS')
    .sort((a, b) => a.position - b.position);
  const terminal = state.exercises.filter((e) => e.status === 'COMPLETED' || e.status === 'SKIPPED')
    .sort((a, b) => a.position - b.position);
  const [drag, setDrag] = useState<Drag | null>(null);
  const dragRef = useRef<Drag | null>(null);
  const listRef = useRef<HTMLOListElement>(null);
  const pointer = useRef<{ x: number; y: number } | null>(null);
  const [message, setMessage] = useState('');
  const enabled = state.status === 'IN_PROGRESS' && pending.length > 1 && !busy;
  const ids = drag?.ids ?? pending.map((e) => e.id);
  const exercises = [...ids.map((id) => pending.find((e) => e.id === id)!), ...terminal];

  useEffect(() => {
    onDraggingChange(drag !== null);
    return () => onDraggingChange(false);
  }, [drag, onDraggingChange]);

  const update = (next: Drag | null) => {
    dragRef.current = next;
    setDrag(next);
  };
  const moveOver = (id: string) => {
    const current = dragRef.current;
    if (!current || id === current.id) return;
    const from = current.ids.indexOf(current.id);
    const to = current.ids.indexOf(id);
    if (to < 0) return; // Completed/skipped rows cannot be drop targets.
    const next = [...current.ids];
    next.splice(from, 1);
    next.splice(to, 0, current.id);
    update({ ...current, ids: next });
    setMessage(`Posizione ${to + 1} di ${next.length}.`);
  };
  const hitTest = (x: number, y: number) => {
    const row = document.elementFromPoint(x, y)?.closest<HTMLElement>('[data-exercise-id]');
    if (row && listRef.current?.contains(row)) moveOver(row.dataset.exerciseId!);
  };
  // Scroll while holding a touch/mouse near the viewport edge, without blocking normal page scrolling.
  const hitRef = useRef(hitTest);
  useEffect(() => { hitRef.current = hitTest; });
  useEffect(() => {
    if (drag?.pointerId == null) return;
    let frame: number;
    const scroll = () => {
      const point = pointer.current;
      if (point) {
        const step = point.y < 70 ? -10 : point.y > window.innerHeight - 70 ? 10 : 0;
        if (step) {
          window.scrollBy(0, step);
          hitRef.current(point.x, point.y);
        }
      }
      frame = requestAnimationFrame(scroll);
    };
    frame = requestAnimationFrame(scroll);
    return () => cancelAnimationFrame(frame);
  }, [drag?.pointerId]);

  const start = (id: string, pointerId: number | null) => {
    if (!enabled || dragRef.current) return;
    update({ id, ids: pending.map((e) => e.id), pointerId });
    setMessage('Esercizio selezionato. Spostalo e rilascia per salvare.');
  };
  const finish = (save: boolean) => {
    const current = dragRef.current;
    if (!current) return;
    update(null);
    pointer.current = null;
    if (save && current.ids.some((id, index) => id !== pending[index]?.id)) {
      onSave(current.ids);
    } else {
      setMessage(save ? 'Ordine invariato.' : 'Spostamento annullato.');
    }
  };
  const pointerEnd = (event: PointerEvent<HTMLButtonElement>, save: boolean) => {
    if (dragRef.current?.pointerId !== event.pointerId) return;
    finish(save);
    if (event.currentTarget.hasPointerCapture?.(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
  };

  return <>
    {state.status === 'IN_PROGRESS' && pending.length > 1 ? (
      <p id="exercise-drag-hint" className="small muted">Trascina la maniglia per cambiare ordine. Il primo esercizio diventa quello corrente. I completati restano in fondo.</p>
    ) : null}
    <span className="visually-hidden" id="exercise-keyboard-hint">Da tastiera: Spazio per selezionare, frecce per spostare, Invio per salvare, Esc per annullare.</span>
    <div className="visually-hidden" role="status">{message}</div>
    <ol ref={listRef} className="list workout-exercises" aria-label="Ordine degli esercizi">
      {exercises.map((e, index) => {
        const movable = pending.some((item) => item.id === e.id) && state.status === 'IN_PROGRESS' && pending.length > 1;
        return <li key={e.id} data-exercise-id={e.id}
          className={`list-item${e.id === state.currentExerciseId ? ' list-item--current' : ''}${e.status === 'COMPLETED' ? ' list-item--done' : ''}${drag?.id === e.id ? ' list-item--dragging' : ''}`}>
          {movable ? <button type="button" className="exercise-drag-handle" aria-label={`Trascina ${e.exerciseName}`}
            aria-describedby="exercise-drag-hint exercise-keyboard-hint" aria-pressed={drag?.id === e.id}
            disabled={busy || (!!drag && drag.id !== e.id)}
            onPointerDown={(event) => {
              if (!enabled || dragRef.current || event.button !== 0 || event.isPrimary === false) return;
              event.preventDefault();
              event.currentTarget.focus();
              start(e.id, event.pointerId);
              pointer.current = { x: event.clientX, y: event.clientY };
              event.currentTarget.setPointerCapture(event.pointerId);
            }}
            onPointerMove={(event) => {
              if (dragRef.current?.pointerId !== event.pointerId) return;
              pointer.current = { x: event.clientX, y: event.clientY };
              hitTest(event.clientX, event.clientY);
            }}
            onPointerUp={(event) => pointerEnd(event, true)}
            onPointerCancel={(event) => pointerEnd(event, false)}
            onLostPointerCapture={() => { if (dragRef.current?.pointerId != null) finish(false); }}
            onBlur={() => { if (dragRef.current?.pointerId === null) finish(false); }}
            onKeyDown={(event) => {
              if (event.key === 'Escape') { event.preventDefault(); finish(false); return; }
              if (event.key === ' ' || event.key === 'Enter') {
                event.preventDefault();
                if (dragRef.current?.id === e.id) finish(true); else start(e.id, null);
              } else if (dragRef.current?.id === e.id && (event.key === 'ArrowUp' || event.key === 'ArrowDown')) {
                event.preventDefault();
                const current = dragRef.current;
                const target = current.ids[current.ids.indexOf(e.id) + (event.key === 'ArrowUp' ? -1 : 1)];
                if (target) moveOver(target);
              }
            }}><GripVertical size={22} aria-hidden="true" /></button> : null}
          <div className="list-item__main">
            <div className="list-item__title"><span className="exercise-row__number" aria-hidden="true">{index + 1}</span>{e.exerciseName}</div>
            <div className="list-item__meta">{e.muscleGroupName} · {e.setsCompleted}/{e.setsPlanned} serie ·{' '}
              {e.sets.map((s) => repsLabel({ reps: s.repsPlanned, toFailure: s.toFailure })).join(' / ')}</div>
          </div>
          <div className="workout-exercise-controls"><ExerciseStatusBadge status={e.status} /></div>
        </li>;
      })}
    </ol>
  </>;
}

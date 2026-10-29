import { useState } from 'react';
import { CalendarClock, CalendarX2, Undo2 } from 'lucide-react';
import { Button } from '../../shared/components/Button';
import { ConfirmDialog } from '../../shared/components/ConfirmDialog';
import { ErrorAlert } from '../../shared/components/Alert';
import { formatLongDate, formatShortDate, todayIso } from '../../shared/utils/format';
import { useScheduleChange, useScheduleOptions, type SchedulePreview } from './changes';

type Pending = 'skip' | 'move' | 'undo' | null;

/**
 * Task 3: manual correction of the planning of one day. Every change is confirmed after showing
 * its consequences on the following training days.
 */
export function ScheduleChangePanel({ assignmentId, date }: { assignmentId: string; date: string }) {
  const options = useScheduleOptions(assignmentId, date);
  const { change, undo } = useScheduleChange(assignmentId);
  const [pending, setPending] = useState<Pending>(null);
  const [target, setTarget] = useState('');
  const data = options.data;
  if (!data || (!data.canSkip && !data.canMove && !data.canUndo)) return null;

  const close = () => { change.reset(); undo.reset(); setPending(null); };
  const error = change.error ?? undo.error;
  // Undoing a past change cannot give the day back: the session is considered lost instead.
  const pastEvent = data.eventKind !== null && date < todayIso();

  return <div className="schedule-change" role="group" aria-label="Modifica la programmazione del giorno">
    <div className="schedule-change__actions">
      {data.canSkip ? <Button variant="secondary" size="sm" icon={<CalendarX2 size={18} aria-hidden="true" />}
        onClick={() => setPending('skip')}>Segna come saltato</Button> : null}
      {data.canMove ? <Button variant="secondary" size="sm" icon={<CalendarClock size={18} aria-hidden="true" />}
        onClick={() => { setTarget(data.moveTargets[0] ?? ''); setPending('move'); }}>Sposta a un altro giorno</Button> : null}
      {data.canUndo ? <Button variant="secondary" size="sm" icon={<Undo2 size={18} aria-hidden="true" />}
        onClick={() => setPending('undo')}>Annulla modifica</Button> : null}
    </div>

    <ConfirmDialog open={pending === 'skip'} title="Segnare il giorno come saltato?" confirmLabel="Salta e riprogramma"
      tone="primary" loading={change.isPending} onCancel={close}
      onConfirm={() => change.mutate({ date }, { onSuccess: close })}>
      <p>“{data.sessionTitle}” non va persa: passa al prossimo giorno di allenamento e le sessioni successive slittano di uno.</p>
      <PreviewTable rows={data.skipPreview} />
      {error ? <ErrorAlert error={error} /> : null}
    </ConfirmDialog>

    <ConfirmDialog open={pending === 'move'} title="Spostare la sessione?" confirmLabel="Sposta sessione"
      tone="primary" loading={change.isPending} onCancel={close}
      onConfirm={() => { if (target) change.mutate({ date, targetDate: target }, { onSuccess: close }); }}>
      <p>“{data.sessionTitle}” verrà svolta nel giorno scelto. Le altre sessioni non cambiano.</p>
      <label className="field">
        <span className="field__label">Nuovo giorno</span>
        <select className="input" value={target} onChange={(e) => setTarget(e.target.value)}>
          {data.moveTargets.map((d) => <option key={d} value={d}>{formatLongDate(d)}</option>)}
        </select>
      </label>
      <p className="small muted">Sono proposti solo i giorni liberi prima del prossimo allenamento di questa scheda.</p>
      {error ? <ErrorAlert error={error} /> : null}
    </ConfirmDialog>

    <ConfirmDialog open={pending === 'undo'} title="Annullare la modifica?" confirmLabel="Annulla modifica"
      tone="primary" loading={undo.isPending} onCancel={close}
      onConfirm={() => { if (data.eventId) undo.mutate(data.eventId, { onSuccess: close }); }}>
      {pastEvent
        ? <p>Il giorno è già passato: verrà considerato svolto senza recuperare “{data.sessionTitle}”, e la rotazione tornerà com’era prima.</p>
        : <p>Torna la programmazione precedente: “{data.sessionTitle}” resta in questo giorno.</p>}
      <PreviewTable rows={data.undoPreview} />
      {error ? <ErrorAlert error={error} /> : null}
    </ConfirmDialog>
  </div>;
}

function PreviewTable({ rows }: { rows: SchedulePreview[] }) {
  if (rows.length === 0) return null;
  return <div className="table-scroll">
    <table className="schedule-preview">
      <caption>Conseguenze sui prossimi giorni</caption>
      <thead><tr><th scope="col">Giorno</th><th scope="col">Prima</th><th scope="col">Dopo</th></tr></thead>
      <tbody>
        {rows.map((row) => <tr key={row.date} className={row.before !== row.after ? 'schedule-preview__changed' : undefined}>
          <th scope="row">{formatShortDate(row.date)}</th>
          <td>{row.before ?? '—'}</td>
          <td>{row.after ?? (row.before ? 'saltato' : '—')}</td>
        </tr>)}
      </tbody>
    </table>
  </div>;
}

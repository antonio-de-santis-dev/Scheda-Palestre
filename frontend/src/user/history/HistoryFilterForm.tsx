import { useState } from 'react';
import { Button } from '../../shared/components/Button';
import { Alert } from '../../shared/components/Alert';
import { historyFilterError, type HistoryFilters } from './filters';

export function HistoryFilterForm({ filters, onApply, onReset }: {
  filters: HistoryFilters; onApply: (filters: HistoryFilters) => void; onReset: () => void;
}) {
  const [error, setError] = useState<string | null>(null);
  return <form className="card form history-filters" aria-label="Filtri storico" onSubmit={(event) => {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const values: HistoryFilters = { from: String(data.get('from') ?? ''), to: String(data.get('to') ?? ''),
      status: String(data.get('status') ?? ''), q: String(data.get('q') ?? '').trim() };
    const invalid = historyFilterError(values);
    setError(invalid);
    if (!invalid) onApply(values);
  }}>
    <div className="form-grid form-grid--2">
      <label className="field">Dal<input className="input" type="date" name="from" defaultValue={filters.from} /></label>
      <label className="field">Al<input className="input" type="date" name="to" defaultValue={filters.to} /></label>
      <label className="field">Esito<select className="input" name="status" defaultValue={filters.status}>
        <option value="">Tutti gli esiti</option>
        <option value="COMPLETED">Completato</option>
        <option value="INTERRUPTED">Interrotto</option>
        <option value="IN_PROGRESS">In corso</option>
      </select></label>
      <label className="field">Nome scheda o sessione<input className="input" type="search" name="q"
        maxLength={100} defaultValue={filters.q} placeholder="Cerca nello storico" /></label>
    </div>
    {error ? <Alert tone="error"><p>{error}</p></Alert> : null}
    <div className="row">
      <Button type="submit">Applica filtri</Button>
      <Button type="button" variant="secondary" onClick={(event) => {
        event.currentTarget.form?.reset(); setError(null); onReset();
      }}>Azzera filtri</Button>
    </div>
  </form>;
}

import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { Music, Plus, Trash2 } from 'lucide-react';
import { PageHeader } from '../../shared/components/PageHeader';
import { Button } from '../../shared/components/Button';
import { Alert, ErrorAlert } from '../../shared/components/Alert';
import { QueryState } from '../../shared/components/States';
import { isApiError } from '../../shared/errors/ApiError';
import { ConfirmDialog } from '../../shared/components/ConfirmDialog';
import { useMusic, type MusicEntry } from './api';
import './music.css';

export function MusicPage() {
  const { query, add, remove } = useMusic();
  const [title, setTitle] = useState('');
  const [url, setUrl] = useState('');
  const [saved, setSaved] = useState(false);
  const [deleting, setDeleting] = useState<MusicEntry | null>(null);
  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (add.isPending) return;
    setSaved(false);
    add.mutate({ title: title.trim(), url: url.trim() }, { onSuccess: () => { setTitle(''); setUrl(''); setSaved(true); } });
  };
  return <div className="stack">
    <PageHeader title="Musica" subtitle="Prepara canzoni e playlist da scegliere durante l’allenamento." />
    <Link to="/app/today" className="btn btn--secondary">Torna all’allenamento di oggi</Link>
    <section className="card stack" aria-labelledby="music-add-title">
      <h2 id="music-add-title"><Music size={22} aria-hidden="true" /> Aggiungi canzone o playlist</h2>
      <p className="muted">Incolla il link condiviso da YouTube e scegli un nome. I link sono salvati nel tuo account. Se il link contiene una playlist, verrà salvata la playlist intera.</p>
      <form className="stack" onSubmit={submit}>
        <label className="stack">Nome<input className="input" value={title} onChange={e => setTitle(e.target.value)} required maxLength={120} placeholder="La mia playlist per allenarmi" /></label>
        <label className="stack">Link YouTube<input className="input" type="url" value={url} onChange={e => setUrl(e.target.value)} required maxLength={2048} placeholder="https://www.youtube.com/..." /></label>
        {add.error ? <ErrorAlert error={add.error} /> : null}
        {isApiError(add.error) ? add.error.fieldErrors.map(error => <p key={error.field} className="small" role="alert">{error.message}</p>) : null}
        <Button type="submit" loading={add.isPending} icon={<Plus size={20} aria-hidden="true" />}>Aggiungi alla mia musica</Button>
        {saved ? <div role="status"><Alert tone="success"><p>Musica aggiunta.</p></Alert></div> : null}
      </form>
    </section>
    <section className="card stack" aria-labelledby="music-library-title">
      <h2 id="music-library-title">La tua musica</h2>
      <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
        {query.data?.length === 0 ? <p>Non hai ancora aggiunto musica. Salva il primo link prima di iniziare.</p> : null}
        <ul className="music-library">{query.data?.map(entry => <li key={entry.id} className="music-library__item">
          <div><strong>{entry.title}</strong><div className="muted small">{entry.kind === 'PLAYLIST' ? 'Playlist' : 'Video / canzone'}</div></div>
          <Button variant="secondary" size="sm" aria-label={`Elimina ${entry.title}`} icon={<Trash2 size={18} aria-hidden="true" />} onClick={() => { remove.reset(); setDeleting(entry); }}>Elimina</Button>
        </li>)}</ul>
      </QueryState>
    </section>
    <ConfirmDialog open={deleting !== null} title="Eliminare dalla tua musica?" confirmLabel="Elimina" loading={remove.isPending}
      onCancel={() => { remove.reset(); setDeleting(null); }}
      onConfirm={() => { if (deleting && !remove.isPending) remove.mutate(deleting.id, { onSuccess: () => setDeleting(null) }); }}>
      <p>Il link di “{deleting?.title}” sarà rimosso dal tuo account. Potrai aggiungerlo di nuovo.</p>
      {/* Shown inside the modal: anything behind it is not visible while it is open. */}
      {remove.error ? <ErrorAlert error={remove.error} /> : null}
    </ConfirmDialog>
  </div>;
}

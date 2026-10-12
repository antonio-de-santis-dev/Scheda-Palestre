import { useState } from 'react';
import { Music, X } from 'lucide-react';
import { Button } from '../../shared/components/Button';
import { QueryState } from '../../shared/components/States';
import { useMusic, type MusicEntry } from './api';
import { YouTubePlayer } from './YouTubePlayer';
import './music.css';

/** Kept outside exercise keys: completing sets/refetching never recreates the player. */
export function WorkoutMusic() {
  const { query } = useMusic();
  const [selected, setSelected] = useState<MusicEntry | null>(null);
  return <section className="card stack workout-music" aria-labelledby="workout-music-title">
    <h2 id="workout-music-title"><Music size={22} aria-hidden="true" /> Musica</h2>
    <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
      {query.data?.length === 0 ? <p className="muted">Nessun link salvato. Puoi aggiungere canzoni e playlist nella sezione Musica prima dell’allenamento.</p> : null}
      {query.data?.length ? <label className="stack">Scegli dalla tua musica
        <select className="input" value={selected?.id ?? ''} onChange={event => setSelected(query.data?.find(entry => entry.id === event.target.value) ?? null)}>
          <option value="">Seleziona canzone o playlist</option>
          {query.data.map(entry => <option key={entry.id} value={entry.id}>{entry.title} · {entry.kind === 'PLAYLIST' ? 'Playlist' : 'Video'}</option>)}
        </select>
      </label> : null}
    </QueryState>
    {selected ? <>
      <p className="muted small">Premi Play nel video. YouTube può mostrare pubblicità; alcuni contenuti non consentono la riproduzione nei siti esterni.</p>
      <div className="music-playback">
      <YouTubePlayer key={selected.id} entry={selected} />
      <Button variant="secondary" size="sm" icon={<X size={18} aria-hidden="true" />} onClick={() => setSelected(null)}>Ferma e chiudi musica</Button>
      </div>
    </> : <p className="muted small">Selezionando un link caricherai il player YouTube e ti collegherai al servizio esterno. La riproduzione richiede una connessione Internet.</p>}
    <p className="muted small">Mantieni il player visibile e la pagina aperta. A schermo bloccato la riproduzione non è garantita.</p>
  </section>;
}

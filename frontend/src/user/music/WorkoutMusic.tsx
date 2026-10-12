import { useState } from 'react';
import { Music, X } from 'lucide-react';
import { Button } from '../../shared/components/Button';
import { QueryState } from '../../shared/components/States';
import { useMusic, type MusicEntry } from './api';
import { YouTubePlayer } from './YouTubePlayer';
import './music.css';

/** Two grid cards share selection state; the player survives workout refetches. */
export function WorkoutMusic() {
  const { query } = useMusic();
  const [selected, setSelected] = useState<MusicEntry | null>(null);
  return <>
    <section className="card workout-music" aria-labelledby="workout-music-title">
      <h2 id="workout-music-title"><Music size={20} aria-hidden="true" /> Musica</h2>
      <QueryState isLoading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
        {query.data?.length === 0 ? <p className="muted small">Nessun link salvato. Aggiungi canzoni e playlist nella sezione Musica prima dell’allenamento.</p> : null}
        {query.data?.length ? <label className="music-choice"><span className="music-choice__label">Scegli canzone o playlist</span>
          <select className="input" value={selected?.id ?? ''} onChange={event => setSelected(query.data?.find(entry => entry.id === event.target.value) ?? null)}>
            <option value="">Seleziona dalla tua musica</option>
            {query.data.map(entry => <option key={entry.id} value={entry.id}>{entry.title} · {entry.kind === 'PLAYLIST' ? 'Playlist' : 'Video'}</option>)}
          </select>
        </label> : null}
      </QueryState>
      <details className="music-help muted small">
        <summary>Come funziona</summary>
        <p>Selezionando un link caricherai il player YouTube e ti collegherai al servizio esterno. Premi Play nel video; serve una connessione Internet.</p>
        <p>YouTube può mostrare pubblicità e alcuni contenuti non consentono la riproduzione nei siti esterni. Tieni aperta la pagina dell’allenamento: a schermo bloccato la riproduzione non è garantita.</p>
      </details>
    </section>
    <section className={`card music-playback${selected ? '' : ' music-playback--empty'}`} aria-label="Riproduzione musicale">
      {selected ? <>
        <div className="music-playback__header"><span className="small" title={selected.title}>{selected.title}</span>
          <Button variant="ghost" size="sm" aria-label="Ferma e chiudi musica" icon={<X size={18} aria-hidden="true" />} onClick={() => setSelected(null)}>Ferma</Button>
        </div>
        <YouTubePlayer key={selected.id} entry={selected} />
      </> : <p className="muted small">Seleziona una canzone o una playlist per visualizzare il video qui.</p>}
    </section>
  </>;
}

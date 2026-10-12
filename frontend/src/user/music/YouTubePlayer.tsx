import { useEffect, useRef, useState } from 'react';
import { Shuffle } from 'lucide-react';
import { Button } from '../../shared/components/Button';
import type { MusicEntry } from './api';
import { embedUrl } from './embed';
import { loadYouTube, type YouTubePlayer as Player } from './youtube';
import { useTrackTitles } from './playlist';

type PlaylistState = { ids: string[]; index: number };
const EMPTY_PLAYLIST: PlaylistState = { ids: [], index: -1 };

export function YouTubePlayer({ entry }: { entry: MusicEntry }) {
  const container = useRef<HTMLDivElement>(null);
  const playerRef = useRef<Player | undefined>(undefined);
  const shuffleWanted = useRef(false);
  const shuffleApplied = useRef(false);
  const [error, setError] = useState('');
  const [playlist, setPlaylist] = useState<PlaylistState>(EMPTY_PLAYLIST);
  const [shuffle, setShuffle] = useState(false);
  const [listOpen, setListOpen] = useState(false);
  const tracksList = useRef<HTMLOListElement>(null);
  const titles = useTrackTitles(playlist.ids, listOpen);

  useEffect(() => {
    if (!listOpen) return;
    const list = tracksList.current;
    const current = list?.querySelector<HTMLElement>('[aria-current="true"]');
    if (list && current) {
      // Keep Claude's current-track shortcut without paging or scrolling the whole page.
      list.scrollTop += current.getBoundingClientRect().top - list.getBoundingClientRect().top;
    }
  }, [listOpen, playlist.index]);

  useEffect(() => {
    let cancelled = false;
    let player: Player | undefined;
    shuffleApplied.current = false;
    const pause = () => player?.pauseVideo();
    const covered = () => document.hidden || !!document.querySelector('dialog[open]');
    const visibility = () => { if (covered()) pause(); };
    const syncPlaylist = () => {
      if (cancelled || !player || entry.kind !== 'PLAYLIST') return;
      const ids = player.getPlaylist() ?? [];
      if (!ids.length) return; // YouTube can populate the list after onReady / first Play.
      if (shuffleApplied.current !== shuffleWanted.current) {
        player.setShuffle(shuffleWanted.current);
        shuffleApplied.current = shuffleWanted.current;
      }
      const index = player.getPlaylistIndex();
      setPlaylist(previous => previous.index === index && previous.ids.length === ids.length && previous.ids.every((id, i) => id === ids[i])
        ? previous : { ids: [...ids], index });
    };
    const observer = new MutationObserver(visibility);
    observer.observe(document.body, { subtree: true, attributes: true, attributeFilter: ['open'] });
    document.addEventListener('visibilitychange', visibility);
    window.addEventListener('pagehide', pause);
    // Native YouTube controls and automatic advancement must also update our selection.
    const interval = entry.kind === 'PLAYLIST' ? window.setInterval(() => { if (!document.hidden) syncPlaylist(); }, 1000) : undefined;
    void loadYouTube().then(api => {
      if (cancelled || !container.current) return;
      const frame = document.createElement('iframe');
      frame.title = `YouTube: ${entry.title}`;
      frame.src = embedUrl(entry, window.location.origin);
      frame.allow = 'autoplay; encrypted-media; fullscreen';
      frame.allowFullscreen = true;
      frame.setAttribute('referrerpolicy', 'strict-origin-when-cross-origin');
      container.current.append(frame);
      player = new api.Player(frame, { events: {
        onReady: event => {
          if (cancelled) return;
          player = event.target;
          playerRef.current = player;
          visibility();
          syncPlaylist();
        },
        onStateChange: event => {
          if (cancelled) return;
          if (event.data === 1) { setError(''); visibility(); }
          syncPlaylist();
        },
        onError: event => { if (!cancelled) setError(event.data === 101 || event.data === 150
          ? 'Questo contenuto non consente la riproduzione nei siti esterni. Scegli un altro link.'
          : 'Impossibile riprodurre questo contenuto YouTube. Scegli un altro link o riprova.'); },
      } });
    }).catch(() => { if (!cancelled) setError('YouTube non è raggiungibile. Controlla la connessione e seleziona di nuovo il link.'); });
    return () => {
      cancelled = true;
      observer.disconnect();
      window.clearInterval(interval);
      document.removeEventListener('visibilitychange', visibility);
      window.removeEventListener('pagehide', pause);
      playerRef.current = undefined;
      player?.destroy();
    };
  }, [entry]);

  const play = (action: (player: Player) => void) => {
    if (!playerRef.current || document.hidden || document.querySelector('dialog[open]')) return;
    container.current?.scrollIntoView({ block: 'nearest', behavior: 'instant' });
    setError('');
    action(playerRef.current);
  };
  const toggleShuffle = () => {
    const wanted = !shuffleWanted.current;
    shuffleWanted.current = wanted;
    setShuffle(wanted);
    if (playerRef.current) {
      playerRef.current.setShuffle(wanted);
      shuffleApplied.current = wanted;
    }
    // Do not reapply on every state event: setShuffle(true) would reshuffle again.
  };
  const loaded = playlist.ids.length > 0;
  return <>
    <div ref={container} className="music-player" />
    {error ? <p role="alert">{error}</p> : null}
    {entry.kind === 'PLAYLIST' ? <div className="playlist-controls">
      <div className="playlist-actions" role="group" aria-label="Controlli playlist">
        <Button variant={shuffle ? 'primary' : 'secondary'} size="sm" aria-pressed={shuffle} disabled={!loaded}
          icon={<Shuffle size={18} aria-hidden="true" />} onClick={toggleShuffle}>Casuale</Button>
      </div>
      {loaded ? <>
        <p className="small muted">{playlist.index >= 0 ? `Brano ${playlist.index + 1} di ${playlist.ids.length}` : `${playlist.ids.length} brani`}
          {shuffle ? ' · Ordine casuale' : ' · Ordine della playlist'}</p>
        <details className="playlist-picker" onToggle={event => setListOpen(event.currentTarget.open)}>
          <summary>Scegli un brano ({playlist.ids.length})</summary>
          {listOpen ? <>
            <ol ref={tracksList} className="playlist-tracks" aria-label="Brani della playlist">
              {playlist.ids.map((id, index) => {
                return <li key={`${index}:${id}`}><button type="button" className="playlist-track" aria-current={index === playlist.index ? 'true' : undefined}
                  aria-label={`Brano ${index + 1}: ${titles[index]?.data ?? (titles[index]?.isLoading ? 'caricamento del titolo' : 'titolo non disponibile')}`} onClick={() => play(player => player.playVideoAt(index))}>
                  <span className="playlist-track__number">{index + 1}</span>
                  <span>{titles[index]?.data ?? `Brano ${index + 1}`}</span>
                  {index === playlist.index ? <span className="playlist-track__current">Attuale</span> : null}
                </button></li>;
              })}
            </ol>
            <p className="small muted">Se un titolo non è disponibile, puoi scegliere il brano dal suo numero.</p>
          </> : null}
        </details>
      </> : <p className="small muted">Premi Play nel video per caricare i brani della playlist. I controlli si attiveranno quando YouTube renderà disponibile l’elenco.</p>}
    </div> : null}
  </>;
}

import { useEffect, useRef, useState } from 'react';
import type { MusicEntry } from './api';
import { embedUrl } from './embed';
import { loadYouTube, type YouTubePlayer as Player } from './youtube';

export function YouTubePlayer({ entry }: { entry: MusicEntry }) {
  const container = useRef<HTMLDivElement>(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let cancelled = false;
    let player: Player | undefined;
    const pause = () => player?.pauseVideo();
    const covered = () => document.hidden || !!document.querySelector('dialog[open]');
    const visibility = () => { if (covered()) pause(); };
    const observer = new MutationObserver(visibility);
    observer.observe(document.body, { subtree: true, attributes: true, attributeFilter: ['open'] });
    document.addEventListener('visibilitychange', visibility);
    window.addEventListener('pagehide', pause);
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
        onReady: visibility,
        onError: event => { if (!cancelled) setError(event.data === 101 || event.data === 150
          ? 'Questo contenuto non consente la riproduzione nei siti esterni. Scegli un altro link.'
          : 'Impossibile riprodurre questo contenuto YouTube. Scegli un altro link o riprova.'); },
      } });
    }).catch(() => { if (!cancelled) setError('YouTube non è raggiungibile. Controlla la connessione e seleziona di nuovo il link.'); });
    return () => {
      cancelled = true;
      observer.disconnect();
      document.removeEventListener('visibilitychange', visibility);
      window.removeEventListener('pagehide', pause);
      player?.destroy();
    };
  }, [entry]);
  return <><div ref={container} className="music-player" />{error ? <p role="alert">{error}</p> : null}</>;
}

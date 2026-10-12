export interface YouTubePlayer {
  pauseVideo(): void;
  destroy(): void;
}
interface PlayerOptions {
  events: { onReady: () => void; onError: (event: { data: number }) => void };
}
export interface YouTubeApi {
  Player: new (element: HTMLIFrameElement, options: PlayerOptions) => YouTubePlayer;
}
declare global {
  interface Window { YT?: YouTubeApi; onYouTubeIframeAPIReady?: () => void }
}
let pending: Promise<YouTubeApi> | undefined;
/** The official API is downloaded only after the user chooses a saved link. */
export function loadYouTube(): Promise<YouTubeApi> {
  if (window.YT?.Player) return Promise.resolve(window.YT);
  if (pending) return pending;
  pending = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = 'https://www.youtube.com/iframe_api';
    script.setAttribute('referrerpolicy', 'strict-origin-when-cross-origin');
    const previous = window.onYouTubeIframeAPIReady;
    const timer = window.setTimeout(() => fail(), 15000);
    const fail = () => {
      window.clearTimeout(timer);
      script.remove();
      window.onYouTubeIframeAPIReady = previous;
      pending = undefined;
      reject(new Error('YouTube non è raggiungibile. Controlla la connessione e riprova.'));
    };
    window.onYouTubeIframeAPIReady = () => {
      window.clearTimeout(timer);
      previous?.();
      if (window.YT?.Player) resolve(window.YT); else fail();
    };
    script.onerror = fail;
    document.head.append(script);
  });
  return pending;
}

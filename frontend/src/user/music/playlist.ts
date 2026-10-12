import { useQueries } from '@tanstack/react-query';

let activeRequests = 0;
const waiting: Array<() => void> = [];
/** All tracks are selectable immediately; title requests use at most four concurrent requests. */
function titleSlot(signal: AbortSignal): Promise<() => void> {
  return new Promise((resolve, reject) => {
    const cancel = () => {
      const index = waiting.indexOf(start);
      if (index >= 0) waiting.splice(index, 1);
      reject(signal.reason);
    };
    const start = () => {
      signal.removeEventListener('abort', cancel);
      if (signal.aborted) { reject(signal.reason); return; }
      activeRequests++;
      resolve(() => { activeRequests--; waiting.shift()?.(); });
    };
    if (signal.aborted) { reject(signal.reason); return; }
    if (activeRequests < 4) start();
    else { waiting.push(start); signal.addEventListener('abort', cancel, { once: true }); }
  });
}

/** Only identifiers returned by the official player become fixed-host metadata requests. */
export function useTrackTitles(ids: string[], enabled: boolean) {
  const uniqueIds = [...new Set(ids)];
  const results = useQueries({ queries: uniqueIds.map(id => ({
    queryKey: ['youtube', 'title', id],
    enabled: enabled && /^[A-Za-z0-9_-]{11}$/.test(id),
    staleTime: 60 * 60 * 1000,
    gcTime: 10 * 60 * 1000,
    retry: false,
    queryFn: async ({ signal }: { signal: AbortSignal }) => {
      const url = new URL('https://www.youtube.com/oembed');
      url.searchParams.set('url', `https://www.youtube.com/watch?v=${id}`);
      url.searchParams.set('format', 'json');
      const release = await titleSlot(signal);
      try {
        const response = await fetch(url, { credentials: 'omit', referrerPolicy: 'strict-origin-when-cross-origin',
          signal: AbortSignal.any([signal, AbortSignal.timeout(10000)]) });
        if (!response.ok) throw new Error('Titolo non disponibile');
        const data: unknown = await response.json();
        if (!data || typeof data !== 'object' || !('title' in data) || typeof data.title !== 'string' || !data.title.trim())
          throw new Error('Titolo non disponibile');
        return data.title.trim().slice(0, 300);
      } finally { release(); }
    },
  })) });
  return ids.map(id => results[uniqueIds.indexOf(id)]);
}

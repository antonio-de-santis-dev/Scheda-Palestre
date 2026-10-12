import { useQueries } from '@tanstack/react-query';

export const TRACKS_PER_PAGE = 20;
/** Only identifiers returned by the official player become fixed-host metadata requests. */
export function useTrackTitles(ids: string[], enabled: boolean) {
  return useQueries({ queries: ids.map(id => ({
    queryKey: ['youtube', 'title', id],
    enabled: enabled && /^[A-Za-z0-9_-]{11}$/.test(id),
    staleTime: 60 * 60 * 1000,
    gcTime: 10 * 60 * 1000,
    retry: false,
    queryFn: async ({ signal }: { signal: AbortSignal }) => {
      const url = new URL('https://www.youtube.com/oembed');
      url.searchParams.set('url', `https://www.youtube.com/watch?v=${id}`);
      url.searchParams.set('format', 'json');
      const response = await fetch(url, { credentials: 'omit', referrerPolicy: 'strict-origin-when-cross-origin',
        signal: AbortSignal.any([signal, AbortSignal.timeout(10000)]) });
      if (!response.ok) throw new Error('Titolo non disponibile');
      const data: unknown = await response.json();
      if (!data || typeof data !== 'object' || !('title' in data) || typeof data.title !== 'string' || !data.title.trim())
        throw new Error('Titolo non disponibile');
      return data.title.trim().slice(0, 300);
    },
  })) });
}

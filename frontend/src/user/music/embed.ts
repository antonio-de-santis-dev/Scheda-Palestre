import type { MusicEntry } from './api';
export function embedUrl(entry: MusicEntry, origin: string) {
  const params = new URLSearchParams({ playsinline: '1', enablejsapi: '1', origin });
  if (entry.kind === 'PLAYLIST') { params.set('listType', 'playlist'); params.set('list', entry.youtubeId); }
  return `https://www.youtube-nocookie.com/embed/${entry.kind === 'VIDEO' ? entry.youtubeId : 'videoseries'}?${params}`;
}

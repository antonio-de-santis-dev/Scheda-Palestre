import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '../../test/server';
import { AppProviders } from '../../app/providers/AppProviders';
import { testQueryClient } from '../../test/render';
import { YouTubePlayer } from './YouTubePlayer';
import { loadYouTube, type YouTubeApi } from './youtube';
import type { MusicEntry } from './api';

vi.mock('./youtube', () => ({ loadYouTube: vi.fn() }));
const ids = ['M7lc1UVf-VE', 'dQw4w9WgXcQ', 'aqz-KE-bpKQ'];
const entry: MusicEntry = { id: 'playlist', title: 'Allenamento', kind: 'PLAYLIST', youtubeId: 'PL1234567890abc' };
type Options = ConstructorParameters<YouTubeApi['Player']>[1];
const handle: { player?: FakePlayer } = {};
let initialIds: string[];
class FakePlayer {
  order: string[];
  index = 0;
  pauseVideo = vi.fn();
  setShuffle = vi.fn((enabled: boolean) => { this.order = enabled ? [...initialIds].reverse() : [...initialIds]; });
  playVideoAt = vi.fn((index: number) => { this.index = index; this.changed(); });
  nextVideo = vi.fn(() => { this.index = (this.index + 1) % this.order.length; this.changed(); });
  previousVideo = vi.fn(() => { this.index = (this.index - 1 + this.order.length) % this.order.length; this.changed(); });
  private frame: HTMLIFrameElement;
  private options: Options;
  constructor(frame: HTMLIFrameElement, options: Options) {
    this.frame = frame; this.options = options;
    this.order = [...initialIds]; handle.player = this;
    queueMicrotask(() => options.events.onReady({ target: this }));
  }
  getPlaylist() { return this.order; }
  getPlaylistIndex() { return this.index; }
  changed() { this.options.events.onStateChange?.({ target: this, data: 1 }); }
  destroy() { this.frame.remove(); }
}
function show(selected: MusicEntry = entry) {
  return render(<AppProviders client={testQueryClient()}><YouTubePlayer key={selected.id} entry={selected} /></AppProviders>);
}
beforeEach(() => {
  initialIds = [...ids];
  vi.mocked(loadYouTube).mockResolvedValue({ Player: FakePlayer });
  server.use(http.get('https://www.youtube.com/oembed', ({ request }) => {
    const id = new URL(new URL(request.url).searchParams.get('url')!).searchParams.get('v');
    return HttpResponse.json({ title: `Canzone ${id}` });
  }));
  Element.prototype.scrollIntoView = vi.fn();
});
describe('controlli playlist', () => {
  it('loads titles only when opened and selects a track without replacing the iframe', async () => {
    const metadata = vi.fn();
    server.use(http.get('https://www.youtube.com/oembed', () => { metadata(); return HttpResponse.json({ title: 'Titolo canzone' }); }));
    const view = show();
    await screen.findByText('Brano 1 di 3 · Ordine della playlist');
    expect(metadata).not.toHaveBeenCalled();
    const frame = screen.getByTitle('YouTube: Allenamento');
    fireEvent.click(screen.getByText('Scegli un brano (3)'));
    await screen.findAllByText('Titolo canzone');
    const tracks = screen.getByRole('list', { name: 'Brani della playlist' });
    fireEvent.click(within(tracks).getAllByRole('button')[1]!);
    expect(handle.player!.playVideoAt).toHaveBeenCalledWith(1);
    expect(await screen.findByText('Brano 2 di 3 · Ordine della playlist')).toBeInTheDocument();
    view.rerender(<AppProviders client={testQueryClient()}><YouTubePlayer key={entry.id} entry={entry} /></AppProviders>);
    expect(screen.getByTitle('YouTube: Allenamento')).toBe(frame);
  });
  it('enables shuffle once, keeps it across video changes and restores the original order', async () => {
    show();
    const random = await screen.findByRole('button', { name: 'Casuale' });
    await waitFor(() => expect(random).toBeEnabled());
    fireEvent.click(random);
    act(() => handle.player!.changed());
    expect(handle.player!.setShuffle).toHaveBeenCalledExactlyOnceWith(true);
    act(() => handle.player!.changed());
    act(() => handle.player!.changed());
    expect(handle.player!.setShuffle).toHaveBeenCalledTimes(1);
    expect(random).toHaveAttribute('aria-pressed', 'true');
    fireEvent.click(random);
    act(() => handle.player!.changed());
    expect(handle.player!.setShuffle).toHaveBeenLastCalledWith(false);
    expect(random).toHaveAttribute('aria-pressed', 'false');
    expect(handle.player!.order).toEqual(ids);
  });
  it('updates the selection for native player changes without redundant navigation buttons', async () => {
    show();
    await screen.findByText('Brano 1 di 3 · Ordine della playlist');
    expect(screen.queryByRole('button', { name: 'Precedente' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Successivo' })).not.toBeInTheDocument();
    act(() => { handle.player!.index = 2; handle.player!.changed(); });
    expect(await screen.findByText('Brano 3 di 3 · Ordine della playlist')).toBeInTheDocument();
  });
  it('keeps playing when scrolled out of view while still pausing for a confirmation dialog', async () => {
    show();
    await screen.findByText('Brano 1 di 3 · Ordine della playlist');
    handle.player!.pauseVideo.mockClear();
    const geometry = vi.spyOn(Element.prototype, 'getBoundingClientRect').mockReturnValue({
      x: 0, y: -1000, top: -1000, bottom: -800, left: 0, right: 300, width: 300, height: 200, toJSON: () => ({}),
    });
    try {
      fireEvent.scroll(window);
      act(() => handle.player!.changed());
      expect(handle.player!.pauseVideo).not.toHaveBeenCalled();
      const dialog = document.createElement('dialog');
      document.body.append(dialog);
      dialog.setAttribute('open', '');
      await waitFor(() => expect(handle.player!.pauseVideo).toHaveBeenCalled());
      dialog.remove();
    } finally { geometry.mockRestore(); }
  });
  it('waits for a late playlist and keeps track selection available when metadata fails', async () => {
    initialIds = [];
    server.use(http.get('https://www.youtube.com/oembed', () => HttpResponse.error()));
    show();
    expect(await screen.findByText(/Premi Play nel video per caricare/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Casuale' })).toBeDisabled();
    act(() => { handle.player!.order = [...ids]; handle.player!.changed(); });
    fireEvent.click(screen.getByText('Scegli un brano (3)'));
    fireEvent.click(within(await screen.findByRole('list', { name: 'Brani della playlist' })).getAllByRole('button')[1]!);
    expect(handle.player!.playVideoAt).toHaveBeenCalledWith(1);
  });
  it('paginates longer playlists using the actual playlist index', async () => {
    initialIds = Array.from({ length: 21 }, (_, i) => `video${String(i).padStart(6, '0')}`);
    show();
    await screen.findByText('Brano 1 di 21 · Ordine della playlist');
    fireEvent.click(screen.getByText('Scegli un brano (21)'));
    fireEvent.click(await screen.findByRole('button', { name: 'Pagina successiva' }));
    await waitFor(() => expect(within(screen.getByRole('list', { name: 'Brani della playlist' })).getAllByRole('button')).toHaveLength(1));
    fireEvent.click(within(screen.getByRole('list', { name: 'Brani della playlist' })).getAllByRole('button')[0]!);
    expect(handle.player!.playVideoAt).toHaveBeenCalledWith(20);
  });
  it('does not add playlist controls to individual songs and clears shuffle on another playlist', async () => {
    const client = testQueryClient();
    const view = render(<AppProviders client={client}><YouTubePlayer key={entry.id} entry={entry} /></AppProviders>);
    await waitFor(() => expect(screen.getByRole('button', { name: 'Casuale' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: 'Casuale' }));
    act(() => handle.player!.changed());
    const second = { ...entry, id: 'second', youtubeId: 'PL0987654321abc' };
    view.rerender(<AppProviders client={client}><YouTubePlayer key={second.id} entry={second} /></AppProviders>);
    expect(await screen.findByRole('button', { name: 'Casuale' })).toHaveAttribute('aria-pressed', 'false');
    view.rerender(<AppProviders client={client}><YouTubePlayer key="video" entry={{ ...entry, id: 'video', kind: 'VIDEO' }} /></AppProviders>);
    expect(screen.queryByRole('button', { name: 'Casuale' })).not.toBeInTheDocument();
  });
});

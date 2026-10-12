import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '../../test/server';
import { normalUser, renderApp } from '../../test/render';
import { WorkoutMusic } from './WorkoutMusic';
import { embedUrl } from './embed';
import { loadYouTube } from './youtube';
vi.mock('./youtube', () => ({ loadYouTube: vi.fn() }));
const pause = vi.fn();
vi.mocked(loadYouTube).mockResolvedValue({ Player: class { pauseVideo = pause; getPlaylist() { return []; } getPlaylistIndex() { return -1; } playVideoAt = vi.fn(); setShuffle = vi.fn(); destroy() { document.querySelector('iframe')?.remove(); } } });
import { AppProviders } from '../../app/providers/AppProviders';
import { testQueryClient } from '../../test/render';

const entry = { id: 'one', title: 'Energia', kind: 'VIDEO' as const, youtubeId: 'M7lc1UVf-VE' };
function mockMusic() {
  server.use(http.get('*/api/auth/me', () => HttpResponse.json(normalUser)), http.get('*/api/me/music', () => HttpResponse.json([entry])));
}
describe('Musica', () => {
  it('adds links through the authenticated API before training', async () => {
    mockMusic();
    const create = vi.fn();
    server.use(http.post('*/api/me/music', async ({ request }) => { create(await request.json()); return HttpResponse.json(entry, { status: 201 }); }));
    renderApp('/app/music');
    await screen.findByRole('heading', { name: 'La tua musica' });
    fireEvent.change(screen.getByLabelText('Nome'), { target: { value: 'Energia' } });
    fireEvent.change(screen.getByLabelText('Link YouTube'), { target: { value: 'https://youtu.be/M7lc1UVf-VE' } });
    fireEvent.click(screen.getByRole('button', { name: 'Aggiungi alla mia musica' }));
    await waitFor(() => expect(create).toHaveBeenCalledWith({ title: 'Energia', url: 'https://youtu.be/M7lc1UVf-VE' }));
    expect(await screen.findByText('Musica aggiunta.')).toBeInTheDocument();
  });
  it('only selects saved links, preserves the player across rerenders and removes it on close', async () => {
    mockMusic();
    const client = testQueryClient();
    const view = render(<AppProviders client={client}><WorkoutMusic /></AppProviders>);
    const select = await screen.findByRole('combobox');
    expect(screen.queryByLabelText('Link YouTube')).not.toBeInTheDocument();
    expect(document.querySelector('iframe')).toBeNull();
    fireEvent.change(select, { target: { value: 'one' } });
    const frame = await screen.findByTitle('YouTube: Energia');
    expect(frame).toHaveAttribute('referrerpolicy', 'strict-origin-when-cross-origin');
    view.rerender(<AppProviders client={client}><WorkoutMusic /></AppProviders>);
    expect(screen.getByTitle('YouTube: Energia')).toBe(frame);
    pause.mockClear();
    const dialog = document.createElement('dialog'); document.body.append(dialog); dialog.setAttribute('open', '');
    await waitFor(() => expect(pause).toHaveBeenCalled());
    dialog.remove();
    fireEvent.click(screen.getByRole('button', { name: 'Ferma e chiudi musica' }));
    expect(document.querySelector('iframe')).toBeNull();
  });
  it('shows a failed deletion inside the confirmation dialog', async () => {
    mockMusic();
    server.use(http.delete('*/api/me/music/:id', () => HttpResponse.json({ code: 'INTERNAL_ERROR' }, { status: 500 })));
    renderApp('/app/music');
    fireEvent.click(await screen.findByRole('button', { name: 'Elimina Energia' }));
    const dialog = await screen.findByRole('dialog');
    fireEvent.click(within(dialog).getByRole('button', { name: 'Elimina' }));
    expect(await within(dialog).findByText(/errore imprevisto/i)).toBeInTheDocument();
  });
  it('treats a link already removed elsewhere as deleted', async () => {
    mockMusic();
    server.use(http.delete('*/api/me/music/:id', () => HttpResponse.json({ code: 'NOT_FOUND' }, { status: 404 })));
    renderApp('/app/music');
    fireEvent.click(await screen.findByRole('button', { name: 'Elimina Energia' }));
    const dialog = await screen.findByRole('dialog');
    fireEvent.click(within(dialog).getByRole('button', { name: 'Elimina' }));
    await waitFor(() => expect(dialog).not.toHaveAttribute('open'));
    expect(screen.queryByText('Elemento non trovato o non accessibile.')).not.toBeInTheDocument();
  });
  it('builds official video and playlist embeds without autoplay', () => {
    expect(embedUrl(entry, 'https://gym.test')).toContain('/embed/M7lc1UVf-VE?');
    const url = new URL(embedUrl({ ...entry, kind: 'PLAYLIST', youtubeId: 'PL1234567890' }, 'https://gym.test'));
    expect(url.pathname).toBe('/embed/videoseries');
    expect(url.searchParams.get('list')).toBe('PL1234567890');
    expect(url.searchParams.has('autoplay')).toBe(false);
  });
});

import { act, render, waitFor } from '@testing-library/react';
import { http as mswHttp } from 'msw';
import { describe, expect, it } from 'vitest';
import { server, problem } from '../../test/server';
import { testQueryClient, normalUser } from '../../test/render';
import { http } from '../../shared/api/http';
import { AUTH_KEY } from '../../auth/useAuth';
import { AppProviders } from './AppProviders';

describe('expired session cache', () => {
  it('removes private data and cancels pending reads after a 401', async () => {
    const client = testQueryClient();
    client.setQueryData(AUTH_KEY, normalUser);
    client.setQueryData(['me', 'history'], { private: 'previous user' });
    render(<AppProviders client={client}><div /></AppProviders>);
    let aborted = false;
    const pending = client.fetchQuery({ queryKey: ['me', 'slow'], queryFn: ({ signal }) =>
      new Promise((resolve) => {
        signal.addEventListener('abort', () => { aborted = true; resolve({ private: true }); }, { once: true });
      }) }).catch(() => undefined);
    server.use(mswHttp.get('*/api/session-check', () => problem(401, 'UNAUTHENTICATED')));
    await act(async () => { await http.get('/api/session-check').catch(() => undefined); });
    await pending;
    await waitFor(() => expect(client.getQueryData(AUTH_KEY)).toBeNull());
    expect(aborted).toBe(true);
    expect(client.getQueryData(['me', 'history'])).toBeUndefined();
    expect(client.getQueryData(['me', 'slow'])).toBeUndefined();
  });
});

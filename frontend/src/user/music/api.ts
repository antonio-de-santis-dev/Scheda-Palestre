import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCurrentUser } from '../../auth/useAuth';
import { http } from '../../shared/api/http';
import { isApiError } from '../../shared/errors/ApiError';

export interface MusicEntry {
  id: string;
  title: string;
  kind: 'VIDEO' | 'PLAYLIST';
  youtubeId: string;
}
export function useMusic() {
  const { data: user } = useCurrentUser();
  const key = ['music', user?.id];
  const client = useQueryClient();
  const query = useQuery({ queryKey: key, enabled: !!user, queryFn: ({ signal }) => http.get<MusicEntry[]>('/api/me/music', undefined, signal) });
  const refresh = () => client.invalidateQueries({ queryKey: key });
  const add = useMutation({ mutationFn: (body: { title: string; url: string }) => http.post<MusicEntry>('/api/me/music', body), onSuccess: refresh });
  // Already removed elsewhere (other tab or device): the goal is reached, so 404 counts as success.
  const remove = useMutation({
    mutationFn: (id: string) => http.del(`/api/me/music/${id}`).catch((error: unknown) => {
      if (isApiError(error) && error.status === 404) return undefined;
      throw error;
    }),
    onSuccess: refresh,
  });
  return { query, add, remove };
}

import { http } from '../shared/api/http';
import { isApiError } from '../shared/errors/ApiError';

export type UserRole = 'ADMIN' | 'USER';

export interface CurrentUser {
  id: string;
  username: string;
  firstName: string;
  lastName: string;
  email: string;
  role: UserRole;
  mustChangePassword: boolean;
}

export const authApi = {
  /** Returns the current user, or null when there is no valid session. */
  async me(): Promise<CurrentUser | null> {
    try {
      return await http.get<CurrentUser>('/api/auth/me');
    } catch (error) {
      if (isApiError(error) && error.status === 401) {
        return null;
      }
      throw error;
    }
  },
  login: (username: string, password: string) => http.post<CurrentUser>('/api/auth/login', { username, password }),
  logout: () => http.post<void>('/api/auth/logout'),
  changePassword: (currentPassword: string, newPassword: string) =>
    http.post<CurrentUser>('/api/auth/change-password', { currentPassword, newPassword }),
};

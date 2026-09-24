import type { RouteObject } from 'react-router';
import { HomeRedirect, RequireAuth } from '../../auth/guards';
import { LoginPage } from '../../auth/LoginPage';
import { ChangePasswordPage } from '../../auth/ChangePasswordPage';
import { AppLayout } from '../layouts/AppLayout';
import { AdminDashboardPage } from '../../admin/AdminDashboardPage';
import { UserHomePage } from '../../user/UserHomePage';
import { NotFoundPage } from './AccessDeniedPage';
import { ADMIN_NAV, USER_NAV } from './navigation';

export const routes: RouteObject[] = [
  { path: '/', element: <HomeRedirect /> },
  { path: '/login', element: <LoginPage /> },
  {
    path: '/change-password',
    element: (
      <RequireAuth allowPasswordChange>
        <ChangePasswordPage />
      </RequireAuth>
    ),
  },
  {
    path: '/admin',
    element: (
      <RequireAuth role="ADMIN">
        <AppLayout home="/admin" areaLabel="amministrazione" items={ADMIN_NAV} />
      </RequireAuth>
    ),
    children: [{ index: true, element: <AdminDashboardPage /> }],
  },
  {
    path: '/app',
    element: (
      <RequireAuth role="USER">
        <AppLayout home="/app" areaLabel="utente" items={USER_NAV} />
      </RequireAuth>
    ),
    children: [{ index: true, element: <UserHomePage /> }],
  },
  { path: '*', element: <NotFoundPage /> },
];

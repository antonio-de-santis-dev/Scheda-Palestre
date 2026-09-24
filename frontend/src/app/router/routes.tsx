import type { ComponentType } from 'react';
import { Navigate, type RouteObject } from 'react-router';
import { HomeRedirect, RequireAuth } from '../../auth/guards';
import { LoginPage } from '../../auth/LoginPage';
import { ChangePasswordPage } from '../../auth/ChangePasswordPage';
import { AppLayout } from '../layouts/AppLayout';
import { LoadingState } from '../../shared/components/States';
import { NotFoundPage } from './AccessDeniedPage';
import { ADMIN_NAV, USER_NAV } from './navigation';

/**
 * Route-level code splitting: each page is downloaded only when first opened, so a USER on a
 * phone never downloads the ADMIN editor.
 */
function page<M>(load: () => Promise<M>, pick: (module: M) => ComponentType): Pick<RouteObject, 'lazy'> {
  return {
    lazy: async () => ({ Component: pick(await load()) }),
  };
}

const fallback = <LoadingState />;

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
    hydrateFallbackElement: fallback,
    element: (
      <RequireAuth role="ADMIN">
        <AppLayout home="/admin" areaLabel="amministrazione" items={ADMIN_NAV} />
      </RequireAuth>
    ),
    children: [
      { index: true, ...page(() => import('../../admin/AdminDashboardPage'), (m) => m.AdminDashboardPage) },
      { path: 'users', ...page(() => import('../../admin/users/UsersPage'), (m) => m.UsersPage) },
      { path: 'users/:id', ...page(() => import('../../admin/users/UserDetailPage'), (m) => m.UserDetailPage) },
      {
        path: 'catalog/muscle-groups',
        ...page(() => import('../../admin/catalog/CatalogPage'), (m) => m.MuscleGroupsPage),
      },
      { path: 'catalog/exercises', ...page(() => import('../../admin/catalog/CatalogPage'), (m) => m.ExercisesPage) },
      { path: 'plans', ...page(() => import('../../admin/plans/PlansPage'), (m) => m.PlansPage) },
      { path: 'plans/:id/edit', ...page(() => import('../../admin/plans/PlanEditorPage'), (m) => m.PlanEditorPage) },
      {
        path: 'plans/:id/assignments',
        ...page(() => import('../../admin/assignments/PlanAssignmentsPage'), (m) => m.PlanAssignmentsPage),
      },
      { path: 'profile', ...page(() => import('../../user/profile/ProfilePage'), (m) => m.ProfilePage) },
    ],
  },
  {
    path: '/app',
    hydrateFallbackElement: fallback,
    element: (
      <RequireAuth role="USER">
        <AppLayout home="/app" areaLabel="utente" items={USER_NAV} />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <Navigate to="/app/today" replace /> },
      { path: 'today', ...page(() => import('../../user/today/TodayPage'), (m) => m.TodayPage) },
      { path: 'workout/:id', ...page(() => import('../../user/workout/WorkoutPage'), (m) => m.WorkoutPage) },
      { path: 'calendar', ...page(() => import('../../user/calendar/CalendarPage'), (m) => m.CalendarPage) },
      { path: 'plans', ...page(() => import('../../user/plans/MyPlansPage'), (m) => m.MyPlansPage) },
      {
        path: 'plans/:assignmentId',
        ...page(() => import('../../user/plans/MyPlanDetailPage'), (m) => m.MyPlanDetailPage),
      },
      { path: 'schedule', ...page(() => import('../../user/schedule/SchedulePage'), (m) => m.SchedulePage) },
      { path: 'history', ...page(() => import('../../user/history/HistoryPages'), (m) => m.HistoryPage) },
      {
        path: 'history/:workoutId',
        ...page(() => import('../../user/history/HistoryPages'), (m) => m.HistoryDetailPage),
      },
      { path: 'profile', ...page(() => import('../../user/profile/ProfilePage'), (m) => m.ProfilePage) },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
];

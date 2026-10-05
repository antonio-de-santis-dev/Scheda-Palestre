import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';

/** MSW server shared by all component tests; each test registers its own handlers. */
export const server = setupServer(
  ...['users', 'plans', 'muscle-groups', 'exercises'].map((resource) =>
    http.get(`*/api/admin/${resource}`, () => HttpResponse.json({ content: [], page: 0, size: 1, totalElements: 0, totalPages: 0 })),
  ),
  // Secondary data loaded by several pages: empty unless a test overrides it.
  http.get('*/api/admin/assignments/recommended-duration-ended', () => HttpResponse.json([])),
  http.get('*/api/me/workout-records', () => HttpResponse.json([])),
  http.get('*/api/me/workout-progress', () => HttpResponse.json([])),
  http.get('*/api/me/workout-stats', () => HttpResponse.json({ totalWorkouts: 0, completedWorkouts: 0,
    interruptedWorkouts: 0, inProgressWorkouts: 0, recordedDurationSeconds: null, workoutsWithDuration: 0,
    workoutsMissingDuration: 0, volume: { recordedKgReps: null, completedSets: 0, recordedSets: 0, missingWeightSets: 0, missingRepsSets: 0 } })),
  http.get('*/api/me/schedules', () => HttpResponse.json([])),
  http.get('*/api/admin/users/:id/activity-report', () =>
    HttpResponse.json({
      userId: 'x',
      generatedOn: '2026-10-05',
      totals: {
        workoutsCompleted: 0,
        workoutsInterrupted: 0,
        workoutsInProgress: 0,
        setsCompleted: 0,
        exercisesCompleted: 0,
        exercisesSkipped: 0,
        firstWorkoutDate: null,
        lastWorkoutDate: null,
      },
      plans: [],
      weeks: [],
    }),
  ),
  http.get('*/api/auth/csrf', () =>
    HttpResponse.json(
      { headerName: 'X-XSRF-TOKEN', token: 'test-token' },
      { headers: { 'Set-Cookie': 'XSRF-TOKEN=test-token; Path=/' } },
    ),
  ),
);

export function problem(status: number, code: string, extra: Record<string, unknown> = {}) {
  return HttpResponse.json(
    { status, code, title: 'Error', detail: code, ...extra },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );
}

import { describe, expect, it } from 'vitest';
import { ApiError } from './ApiError';
import { errorMessage, hasMessageFor } from './messages';

describe('error messages', () => {
  it('translates every execution and schedule code returned by the backend', () => {
    ['INVALID_EXERCISE_ORDER', 'WORKOUT_STATE_CHANGED', 'SET_RESULTS_WINDOW_CLOSED', 'SCHEDULE_DAYS_REQUIRED',
      'METHOD_NOT_ALLOWED', 'PAYLOAD_TOO_LARGE', 'UNSUPPORTED_MEDIA_TYPE', 'REQUEST_ERROR', 'UNPROCESSABLE']
      .forEach((code) => expect(hasMessageFor(code), code).toBe(true));
  });

  it('maps a 429 without Problem Details (Nginx rate limit) to an Italian message', () => {
    const error = ApiError.fromProblem(429, null);
    expect(error.code).toBe('RATE_LIMITED');
    expect(errorMessage(error)).toBe('Troppi tentativi in poco tempo. Attendi un minuto e riprova.');
  });

  it('never shows the technical English detail for a known code', () => {
    const error = ApiError.fromProblem(409, { code: 'SET_RESULTS_WINDOW_CLOSED', detail: 'Results can only be entered…' });
    expect(errorMessage(error)).toMatch(/^Il recupero di questa serie è terminato/);
  });
});

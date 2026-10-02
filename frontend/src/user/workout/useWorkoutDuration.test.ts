import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { workoutState } from '../../test/workoutFixtures';
import { elapsedWorkoutSeconds, useWorkoutDuration } from './useWorkoutDuration';
import { formatWorkoutDuration } from '../../shared/utils/format';

afterEach(() => vi.useRealTimers());

describe('workout elapsed duration', () => {
  it('uses server time despite a client clock offset and freezes at the finish time', () => {
    const receivedAt = Date.parse('2026-10-05T07:00:00Z');
    const state = { ...workoutState({ startedAt: '2026-10-05T08:00:00Z', serverTime: '2026-10-05T08:02:00Z' }), receivedAt };
    expect(elapsedWorkoutSeconds(state, receivedAt + 5000)).toBe(125);
    expect(elapsedWorkoutSeconds({ ...state, finishedAt: '2026-10-05T09:01:01Z' }, receivedAt + 10000)).toBe(3661);
    expect(formatWorkoutDuration(3661)).toBe('1:01:01');
    expect(formatWorkoutDuration(90061)).toBe('25:01:01');
  });

  it('keeps elapsed workout time running during paused recovery and removes ticks after completion', () => {
    vi.useFakeTimers();
    const now = Date.now();
    const state = { ...workoutState({ startedAt: new Date(now).toISOString(), serverTime: new Date(now).toISOString(), restPaused: true }), receivedAt: now };
    const { result, rerender, unmount } = renderHook(({ value }) => useWorkoutDuration(value), { initialProps: { value: state } });
    act(() => vi.advanceTimersByTime(5000));
    expect(result.current).toBe(5);
    rerender({ value: { ...state, status: 'COMPLETED', finishedAt: new Date(now + 5000).toISOString() } });
    act(() => vi.advanceTimersByTime(2000));
    expect(result.current).toBe(5);
    expect(vi.getTimerCount()).toBe(0);
    unmount();
  });
});

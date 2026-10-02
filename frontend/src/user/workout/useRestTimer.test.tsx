import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useRestTimer } from './useRestTimer';

afterEach(() => vi.useRealTimers());

describe('rest timer lifecycle', () => {
  it('stops ticking at expiry and notifies once even after a server refresh', () => {
    vi.useFakeTimers();
    const start = Date.now();
    const onFinished = vi.fn();
    const input = {
      restEndsAt: new Date(start + 1000).toISOString(),
      serverTime: new Date(start).toISOString(),
      receivedAt: start,
    };
    const { result, rerender } = renderHook(
      ({ value }) => useRestTimer(value, vi.fn(), onFinished),
      { initialProps: { value: input } },
    );
    expect(result.current).toBe(1);
    act(() => vi.advanceTimersByTime(1000));
    expect(result.current).toBe(0);
    expect(onFinished).toHaveBeenCalledTimes(1);
    expect(vi.getTimerCount()).toBe(0);

    rerender({ value: { ...input, serverTime: new Date(start + 2000).toISOString(), receivedAt: start + 2000 } });
    act(() => vi.advanceTimersByTime(250));
    expect(onFinished).toHaveBeenCalledTimes(1);
    expect(vi.getTimerCount()).toBe(0);

    const now = Date.now();
    rerender({ value: { restEndsAt: new Date(now + 1000).toISOString(), serverTime: new Date(now).toISOString(), receivedAt: now } });
    act(() => vi.advanceTimersByTime(1000));
    expect(onFinished).toHaveBeenCalledTimes(2);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('removes scheduled ticks when the workout view unmounts', () => {
    vi.useFakeTimers();
    const now = Date.now();
    const finished = vi.fn();
    const { unmount } = renderHook(() => useRestTimer({
      restEndsAt: new Date(now + 60_000).toISOString(), serverTime: new Date(now).toISOString(), receivedAt: now,
    }, vi.fn(), finished));
    unmount();
    act(() => vi.advanceTimersByTime(60_000));
    expect(vi.getTimerCount()).toBe(0);
    expect(finished).not.toHaveBeenCalled();
  });
});

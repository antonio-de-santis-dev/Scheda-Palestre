import { useEffect, useRef, useState } from 'react';

/**
 * Remaining rest in milliseconds (spec 10.9): computed from server instants, never by blindly
 * decrementing a counter. The offset corrects the difference between server and client clocks.
 */
export function remainingRestMs(restEndsAt: string, serverTime: string, receivedAt: number, nowMs: number): number {
  const offset = Date.parse(serverTime) - receivedAt;
  return Math.max(0, Date.parse(restEndsAt) - (nowMs + offset));
}

interface TimerInput {
  restEndsAt: string | null;
  serverTime: string;
  receivedAt: number;
}

/**
 * Ticks while a rest is running and re-aligns with the server when the page becomes visible
 * again ({@code onVisible}). Returns the remaining whole seconds (0 when no rest is running).
 */
export function useRestTimer(input: TimerInput | undefined, onVisible: () => void, onFinished?: () => void) {
  const [now, setNow] = useState(() => Date.now());
  const callbacks = useRef({ onVisible, onFinished });
  useEffect(() => {
    callbacks.current = { onVisible, onFinished };
  });

  const restEndsAt = input?.restEndsAt ?? null;
  const serverTime = input?.serverTime ?? null;
  const receivedAt = input?.receivedAt ?? 0;

  useEffect(() => {
    if (!restEndsAt || !serverTime) {
      return undefined;
    }
    let running = true;
    const tick = () => {
      const current = Date.now();
      setNow(current);
      if (running && remainingRestMs(restEndsAt, serverTime, receivedAt, current) <= 0) {
        running = false;
        callbacks.current.onFinished?.();
      }
    };
    const first = window.setTimeout(tick, 0);
    const id = window.setInterval(tick, 250);
    return () => {
      window.clearTimeout(first);
      window.clearInterval(id);
    };
  }, [restEndsAt, serverTime, receivedAt]);

  useEffect(() => {
    const handler = () => {
      if (document.visibilityState === 'visible') {
        setNow(Date.now());
        callbacks.current.onVisible();
      }
    };
    document.addEventListener('visibilitychange', handler);
    return () => document.removeEventListener('visibilitychange', handler);
  }, []);

  const remainingMs = restEndsAt && serverTime ? remainingRestMs(restEndsAt, serverTime, receivedAt, now) : 0;
  return Math.ceil(remainingMs / 1000);
}

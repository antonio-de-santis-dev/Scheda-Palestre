import { afterEach, describe, expect, it, vi } from 'vitest';
import { celebrate } from './feedback';

const fire = vi.hoisted(() => vi.fn());
const create = vi.hoisted(() => vi.fn<(_canvas: HTMLCanvasElement, _options: unknown) => typeof fire>(() => fire));
vi.mock('canvas-confetti', () => ({ default: { create } }));
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); });

describe('confetti viewport overlay', () => {
  it('uses the visible mobile viewport, follows resize and removes the overlay after the burst', async () => {
    const viewport = new EventTarget() as EventTarget & { width: number; height: number; offsetLeft: number; offsetTop: number };
    Object.assign(viewport, { width: 390, height: 640, offsetLeft: 0, offsetTop: 20 });
    vi.stubGlobal('visualViewport', viewport);
    let finish!: () => void;
    fire.mockImplementationOnce(() => new Promise<void>((resolve) => { finish = resolve; }));
    const pending = celebrate('exercise');
    await vi.waitFor(() => expect(create).toHaveBeenCalledOnce());
    const canvas = create.mock.calls[0]![0] as unknown as HTMLCanvasElement;
    expect(canvas.parentElement).toBe(document.body);
    expect(canvas.style.width).toBe('390px'); expect(canvas.style.height).toBe('640px');
    expect(canvas.style.top).toBe('20px'); expect(canvas.style.position).toBe('fixed');
    expect(canvas.style.pointerEvents).toBe('none'); expect(canvas.style.zIndex).toBe('1000');
    expect(canvas.getAttribute('aria-hidden')).toBe('true');
    viewport.height = 420; viewport.dispatchEvent(new Event('resize'));
    expect(canvas.style.height).toBe('420px');
    expect(create).toHaveBeenCalledWith(canvas, { resize: true, useWorker: false, disableForReducedMotion: true });
    expect(fire).toHaveBeenCalledWith(expect.objectContaining({ origin: { x: 0.5, y: 0.65 } }));
    finish(); await pending; expect(canvas.isConnected).toBe(false);
  });

  it('keeps reduced-motion preferences and cleans the overlay after rendering fails', async () => {
    vi.stubGlobal('matchMedia', () => ({ matches: true }));
    await celebrate('workout'); expect(create).not.toHaveBeenCalled();
    vi.stubGlobal('matchMedia', () => ({ matches: false }));
    fire.mockRejectedValueOnce(new Error('Canvas unavailable'));
    await celebrate('workout');
    expect(create).toHaveBeenCalledOnce();
    expect(document.querySelector('.workout-confetti')).toBeNull();
  });
});

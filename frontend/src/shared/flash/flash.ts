/**
 * One-shot notification carried by the React Router navigation state (never by query params,
 * module variables or storage): it survives exactly one navigation and is cleared as soon as it
 * is shown, so refresh and "back" never replay it.
 */
export interface Flash {
  tone: 'success' | 'info';
  title: string;
  message?: string;
}

export interface FlashState {
  flash: Flash;
}

export function flashState(flash: Flash): FlashState {
  return { flash };
}

export function readFlash(state: unknown): Flash | null {
  if (state && typeof state === 'object' && 'flash' in state) {
    const flash = (state as FlashState).flash;
    return flash && typeof flash.title === 'string' ? flash : null;
  }
  return null;
}

/** "a 1 utente" / "a 3 utenti": Italian singular/plural agreement. */
export function usersCount(count: number): string {
  return `${count} ${count === 1 ? 'utente' : 'utenti'}`;
}

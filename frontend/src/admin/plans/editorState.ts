/** Navigation state marking an editor opened right after "Crea e apri l'editor" or "Duplica". */
export const EDITOR_NEW_PLAN = { newPlan: true } as const;

export function isNewPlan(state: unknown): boolean {
  return typeof state === 'object' && state !== null && 'newPlan' in state && state.newPlan === true;
}

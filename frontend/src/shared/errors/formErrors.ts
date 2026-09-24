import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';
import { isApiError } from './ApiError';

/** Italian translations for backend field messages we know about. */
const FIELD_MESSAGES: Record<string, string> = {
  'Current password is not correct': 'La password attuale non è corretta.',
  'New password must differ from the current one': 'La nuova password deve essere diversa da quella attuale.',
  'Password must be 8-128 characters long and contain at least one letter and one digit':
    'La password deve avere 8-128 caratteri e contenere almeno una lettera e una cifra.',
  'Username is already in use': 'Username già in uso.',
  'Email is already in use': 'Email già in uso.',
  'Name is already in use': 'Nome già in uso.',
};

export function translateFieldMessage(message: string): string {
  return FIELD_MESSAGES[message] ?? message;
}

/**
 * Copies backend field errors onto a react-hook-form form, keeping the values the user typed.
 * Returns true when at least one error was attached to a known field.
 */
export function applyServerErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  fields: readonly Path<T>[],
): boolean {
  if (!isApiError(error) || error.fieldErrors.length === 0) {
    return false;
  }
  let applied = false;
  for (const fieldError of error.fieldErrors) {
    const match = fields.find((f) => f === fieldError.field);
    if (match) {
      setError(match, { type: 'server', message: translateFieldMessage(fieldError.message) });
      applied = true;
    }
  }
  return applied;
}

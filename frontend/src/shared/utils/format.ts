/** Dates are shown in the client's time zone (spec 21); calendar dates are plain YYYY-MM-DD. */

const dateFormatter = new Intl.DateTimeFormat('it-IT', { day: 'numeric', month: 'long', year: 'numeric' });
const shortDateFormatter = new Intl.DateTimeFormat('it-IT', { weekday: 'short', day: 'numeric', month: 'short' });
const longDateFormatter = new Intl.DateTimeFormat('it-IT', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
});
const dateTimeFormatter = new Intl.DateTimeFormat('it-IT', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

/** Parses a calendar date (YYYY-MM-DD) as a local date, without time zone shifts. */
export function parseLocalDate(value: string): Date {
  const [y, m, d] = value.split('-').map(Number);
  return new Date(y ?? 1970, (m ?? 1) - 1, d ?? 1);
}

export function toIsoDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function todayIso(): string {
  return toIsoDate(new Date());
}

export function addDays(iso: string, days: number): string {
  const date = parseLocalDate(iso);
  date.setDate(date.getDate() + days);
  return toIsoDate(date);
}

export function formatDate(iso: string | null | undefined): string {
  return iso ? dateFormatter.format(parseLocalDate(iso)) : '—';
}

export function formatShortDate(iso: string): string {
  return shortDateFormatter.format(parseLocalDate(iso));
}

export function formatLongDate(iso: string): string {
  return longDateFormatter.format(parseLocalDate(iso));
}

export function formatDateTime(instant: string | null | undefined): string {
  return instant ? dateTimeFormatter.format(new Date(instant)) : '—';
}

export const WEEKDAYS = [
  { value: 1, label: 'Lunedì', short: 'Lun' },
  { value: 2, label: 'Martedì', short: 'Mar' },
  { value: 3, label: 'Mercoledì', short: 'Mer' },
  { value: 4, label: 'Giovedì', short: 'Gio' },
  { value: 5, label: 'Venerdì', short: 'Ven' },
  { value: 6, label: 'Sabato', short: 'Sab' },
  { value: 7, label: 'Domenica', short: 'Dom' },
] as const;

/** Formats seconds as m:ss (e.g. 90 -> "1:30"). */
export function formatDuration(totalSeconds: number): string {
  const safe = Math.max(0, Math.round(totalSeconds));
  const minutes = Math.floor(safe / 60);
  const seconds = safe % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

export function formatRest(seconds: number): string {
  if (seconds === 0) {
    return 'nessun recupero';
  }
  return seconds < 60 ? `${seconds}"` : formatDuration(seconds);
}

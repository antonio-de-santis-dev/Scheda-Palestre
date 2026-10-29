export interface HistoryFilters { from: string; to: string; status: string; q: string }
export const HISTORY_FILTER_KEYS = ['from', 'to', 'status', 'q'] as const;

export function readHistoryFilters(params: URLSearchParams): HistoryFilters {
  return { from: params.get('from') ?? '', to: params.get('to') ?? '',
    status: params.get('status') ?? '', q: (params.get('q') ?? '').trim() };
}

function validDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

export function historyFilterError(filters: HistoryFilters): string | null {
  if ((filters.from && !validDate(filters.from)) || (filters.to && !validDate(filters.to))) return 'Inserisci date valide.';
  if (filters.from && filters.to && filters.from > filters.to) return 'La data iniziale deve precedere o coincidere con quella finale.';
  if (filters.status && !['COMPLETED', 'INTERRUPTED', 'IN_PROGRESS'].includes(filters.status)) return 'Esito dell’allenamento non valido.';
  if (filters.q.length > 100) return 'Il nome può contenere al massimo 100 caratteri.';
  return null;
}

export function historyPage(params: URLSearchParams): number {
  const value = params.get('page') ?? '0';
  const number = Number(value);
  return /^\d+$/.test(value) && Number.isSafeInteger(number) && number <= 1_000_000 ? number : 0;
}

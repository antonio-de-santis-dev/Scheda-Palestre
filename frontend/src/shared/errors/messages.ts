import { isApiError } from './ApiError';

/** Italian messages for the application error codes returned by the backend. */
const MESSAGES: Record<string, string> = {
  NETWORK_ERROR: 'Connessione assente o server non raggiungibile. Controlla la rete e riprova.',
  TIMEOUT: 'Il server non ha risposto in tempo. Riprova.',
  INVALID_CREDENTIALS: 'Credenziali non valide.',
  UNAUTHENTICATED: 'La sessione è scaduta. Accedi di nuovo.',
  FORBIDDEN: 'Non hai i permessi per questa operazione.',
  CSRF_INVALID: 'Sessione di sicurezza scaduta. Riprova.',
  PASSWORD_CHANGE_REQUIRED: 'Devi cambiare la password prima di continuare.',
  NOT_FOUND: 'Elemento non trovato o non accessibile.',
  VALIDATION_ERROR: 'Alcuni campi non sono validi. Controlla i dati inseriti.',
  BAD_REQUEST: 'Richiesta non valida.',
  CONFLICT: 'I dati sono in conflitto con lo stato attuale. Ricarica e riprova.',
  CONCURRENT_MODIFICATION: 'I dati sono stati modificati da qualcun altro. Ricarica la pagina e riprova.',
  INTERNAL_ERROR: 'Si è verificato un errore imprevisto. Riprova più tardi.',
  USERNAME_TAKEN: 'Username già in uso.',
  EMAIL_TAKEN: 'Email già in uso.',
  CANNOT_DEACTIVATE_SELF: 'Non puoi disattivare il tuo account.',
  LAST_ACTIVE_ADMIN: 'Deve sempre esistere almeno un ADMIN attivo.',
  NAME_TAKEN: 'Esiste già un elemento con questo nome.',
  CATALOG_ITEM_INACTIVE: "L'elemento selezionato è disattivato e non può essere usato.",
  PLAN_DELETED: 'La scheda è eliminata: ripristinala prima di modificarla.',
  PLAN_NOT_EXECUTABLE:
    'La scheda non è eseguibile: servono almeno una sessione e un esercizio valido in ogni sessione.',
  MUSCLE_GROUP_ALREADY_IN_SESSION: 'Questo gruppo muscolare è già presente nella sessione.',
  INVALID_ORDER: "L'ordine inviato non corrisponde agli elementi attuali. Ricarica e riprova.",
  INVALID_CUSTOM_SETS: 'Le serie personalizzate devono coprire tutte le serie da 1 a N.',
  USER_NOT_ASSIGNABLE: 'Uno o più utenti selezionati non possono ricevere schede.',
  ASSIGNMENT_ALREADY_ACTIVE: "L'utente ha già questa scheda attiva.",
  ASSIGNMENT_CLOSED: "L'assegnazione è chiusa e non può essere riattivata.",
  ASSIGNMENT_ALREADY_CLOSED: "L'assegnazione è già chiusa.",
  NO_ACTIVE_ASSIGNMENT: 'Non hai una scheda attiva.',
  NOT_A_TRAINING_DAY: 'La data scelta non è un giorno di allenamento.',
  DATE_NOT_ALLOWED: "Puoi avviare solo l'allenamento di oggi.",
  WORKOUT_ALREADY_EXISTS: "L'allenamento di questo giorno è già stato avviato.",
  WORKOUT_ALREADY_IN_PROGRESS: 'Hai già un allenamento in corso: concludilo o interrompilo prima.',
  WORKOUT_NOT_IN_PROGRESS: "L'allenamento non è più in corso.",
  SET_NOT_CURRENT: 'Questa serie non è quella corrente. La schermata è stata aggiornata.',
  EXERCISE_NOT_IN_PROGRESS: 'Solo l’esercizio in corso può essere saltato.',
  RANGE_TOO_LARGE: "L'intervallo richiesto è troppo ampio (massimo 62 giorni).",
  EXERCISE_GROUP_MISMATCH: 'L’esercizio scelto non appartiene al gruppo muscolare di questa sezione.',
  SCHEDULE_DAY_CONFLICT: 'Alcuni giorni sono già usati da un’altra tua scheda attiva.',
  REST_STATE_CHANGED: 'Il recupero è stato modificato da un’altra richiesta. La schermata è stata aggiornata.',
  REST_NOT_ACTIVE: 'Non c’è un recupero attivo da modificare.',
  REST_ALREADY_PAUSED: 'Il recupero è già in pausa.',
  REST_NOT_PAUSED: 'Il recupero è già in esecuzione.',
  REST_LIMIT_EXCEEDED: 'Il recupero non può superare un’ora.',
  REST_NOT_FINISHED: 'Il recupero non è ancora finito: la schermata è stata allineata al server.',
  CANNOT_DELETE_SELF: 'Non puoi eliminare il tuo account.',
  PROTECTED_ACCOUNT: "L'account ADMIN iniziale non può essere eliminato.",
  ACCOUNT_DELETED: "L'account è stato eliminato e non può essere modificato.",
  ASSIGNMENT_NOT_ACTIVE: 'La scheda non è attiva: non puoi sceglierne i giorni.',
};

const DAY_NAMES = ['', 'lunedì', 'martedì', 'mercoledì', 'giovedì', 'venerdì', 'sabato', 'domenica'];

/** "lunedì e venerdì", "lunedì, martedì e giovedì". */
function joinItalian(items: string[]): string {
  return items.length <= 1 ? (items[0] ?? '') : `${items.slice(0, -1).join(', ')} e ${items[items.length - 1]}`;
}

interface DayConflict {
  weekday: number;
  planName: string;
}

const text = (value: unknown): string | null => (typeof value === 'string' && value.trim() ? value : null);

/** Messages that use the extra Problem Details members to say exactly what is wrong. */
const DETAILED: Record<string, (problem: Record<string, unknown>) => string | null> = {
  SCHEDULE_DAY_CONFLICT: (p) => {
    const conflicts = Array.isArray(p.conflicts) ? (p.conflicts as DayConflict[]) : [];
    if (conflicts.length === 0) {
      return null;
    }
    const byPlan = new Map<string, string[]>();
    conflicts.forEach((c) => byPlan.set(c.planName, [...(byPlan.get(c.planName) ?? []), DAY_NAMES[c.weekday] ?? '?']));
    const parts = [...byPlan].map(([plan, days]) => `${joinItalian(days)} ${days.length === 1 ? 'è già usato' : 'sono già usati'} da “${plan}”`);
    return `${parts.join('; ')}. Scegli altri giorni oppure libera prima quelli dell’altra scheda.`.replace(/^./, (c) => c.toUpperCase());
  },
  EXERCISE_GROUP_MISMATCH: (p) => {
    const exercise = text(p.exerciseName);
    const group = text(p.muscleGroupName);
    return exercise && group ? `“${exercise}” non appartiene al gruppo ${group}: scegli un esercizio di questo gruppo.` : null;
  },
};

export function errorMessage(error: unknown, fallback = MESSAGES.INTERNAL_ERROR): string {
  if (isApiError(error)) {
    return DETAILED[error.code]?.(error.problem) ?? MESSAGES[error.code] ?? error.detail ?? fallback ?? 'Errore';
  }
  return fallback ?? 'Errore';
}

export function hasMessageFor(code: string): boolean {
  return code in MESSAGES;
}

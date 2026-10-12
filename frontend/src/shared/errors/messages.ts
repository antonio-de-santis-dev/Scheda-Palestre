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
  SET_RESULTS_CHANGED: 'Questa serie è già stata registrata con altri risultati. La schermata è stata aggiornata.',
  SET_NOT_CURRENT: 'Questa serie non è quella corrente. La schermata è stata aggiornata.',
  EXERCISE_NOT_IN_PROGRESS: 'Solo l’esercizio in corso può essere saltato.',
  INVALID_HISTORY_FILTER: 'Controlla l’intervallo di date e il nome inseriti nei filtri.',
  RANGE_TOO_LARGE: "L'intervallo richiesto è troppo ampio (massimo 62 giorni).",
  EXERCISE_GROUP_MISMATCH: 'L’esercizio scelto non appartiene al gruppo muscolare di questa sezione.',
  SCHEDULE_DAY_CONFLICT: 'Alcuni giorni sono già usati da un’altra tua scheda attiva.',
  REST_STATE_CHANGED: 'Il recupero o l’allenamento è cambiato: la schermata è stata aggiornata.',
  REST_NOT_ACTIVE: 'Il recupero è già terminato: la schermata è stata aggiornata.',
  REST_ALREADY_PAUSED: 'Il recupero è già in pausa.',
  REST_NOT_PAUSED: 'Il recupero non è in pausa.',
  REST_NOT_FINISHED: 'Il recupero non è ancora finito: la schermata è stata allineata al server.',
  CANNOT_DELETE_SELF: 'Non puoi eliminare il tuo account.',
  PROTECTED_ACCOUNT: "L'account ADMIN iniziale è protetto: non può essere eliminato né cambiare username.",
  ACCOUNT_DELETED: "L'account è stato eliminato e non può essere modificato.",
  ASSIGNMENT_NOT_ACTIVE: 'La scheda non è attiva: non puoi sceglierne i giorni.',
  SCHEDULE_DAYS_REQUIRED: 'Scegli e salva prima almeno un giorno di allenamento.',
  INVALID_EXERCISE_ORDER: "L'ordine degli esercizi non è più valido: la schermata è stata aggiornata.",
  WORKOUT_STATE_CHANGED: "L'allenamento è cambiato nel frattempo: la schermata è stata aggiornata.",
  SET_RESULTS_WINDOW_CLOSED: 'Il recupero di questa serie è terminato: i risultati non possono più essere modificati.',
  RATE_LIMITED: 'Troppi tentativi in poco tempo. Attendi un minuto e riprova.',
  UNPROCESSABLE: "L'operazione non è consentita nello stato attuale.",
  METHOD_NOT_ALLOWED: 'Operazione non supportata.',
  PAYLOAD_TOO_LARGE: 'I dati inviati sono troppo grandi.',
  UNSUPPORTED_MEDIA_TYPE: 'Formato della richiesta non supportato.',
  REQUEST_ERROR: 'La richiesta non può essere elaborata.',
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

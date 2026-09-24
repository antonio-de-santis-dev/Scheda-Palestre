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
};

export function errorMessage(error: unknown, fallback = MESSAGES.INTERNAL_ERROR): string {
  if (isApiError(error)) {
    return MESSAGES[error.code] ?? error.detail ?? fallback ?? 'Errore';
  }
  return fallback ?? 'Errore';
}

export function hasMessageFor(code: string): boolean {
  return code in MESSAGES;
}

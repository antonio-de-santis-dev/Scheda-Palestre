import { CalendarX2 } from 'lucide-react';
import { formatDate } from '../utils/format';

/**
 * Shown from the final recommended week; the server determines the phase in the gym's time zone.
 * The notice never hides or blocks plan actions.
 */
export function RecommendedDurationNotice({ planName, expiresOn, ended = true }: { planName: string; expiresOn: string; ended?: boolean }) {
  return (
    <div className="alert alert--warning" role="status">
      <CalendarX2 size={22} aria-hidden="true" />
      <div className="alert__body">
        <div className="alert__title">{ended ? 'Durata consigliata terminata' : 'Durata consigliata in scadenza'}: “{planName}”</div>
        <p>
          {ended ? 'La durata consigliata è terminata' : 'La durata consigliata terminerà'} il {formatDate(expiresOn)}. Contatta la palestra per riceverne una
          nuova. Nel frattempo puoi continuare a usarla normalmente.
        </p>
      </div>
    </div>
  );
}

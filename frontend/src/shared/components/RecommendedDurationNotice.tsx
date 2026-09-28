import { CalendarX2 } from 'lucide-react';
import { formatDate } from '../utils/format';

/**
 * ADR 0009: shown from the day after the end of the recommended duration (computed by the
 * server in the gym's time zone). Informative only: it never hides or blocks the plan actions.
 */
export function RecommendedDurationNotice({ planName, expiresOn }: { planName: string; expiresOn: string }) {
  return (
    <div className="alert alert--warning" role="status">
      <CalendarX2 size={22} aria-hidden="true" />
      <div className="alert__body">
        <div className="alert__title">Durata consigliata terminata: “{planName}”</div>
        <p>
          La durata consigliata della scheda è terminata il {formatDate(expiresOn)}. Contatta la palestra per riceverne una
          nuova. Nel frattempo puoi continuare a usarla normalmente.
        </p>
      </div>
    </div>
  );
}

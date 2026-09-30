import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { CheckCircle2, Info, X } from 'lucide-react';
import { readFlash, type Flash } from './flash';

/** Long enough to be read (WCAG 2.2.1): the user can also close it at any time. */
export const FLASH_DURATION_MS = 8000;

/**
 * Shows the flash message carried by the navigation state. It lives in the persistent layout so
 * the polite live region already exists when the message is inserted (reliable announcement)
 * and focus is never moved.
 */
export function FlashOutlet() {
  const location = useLocation();
  const navigate = useNavigate();
  const incoming = readFlash(location.state);
  const [shown, setShown] = useState<{ key: string; flash: Flash } | null>(null);

  // Adjust state during render: each navigation entry is shown at most once.
  if (incoming && shown?.key !== location.key) {
    setShown({ key: location.key, flash: incoming });
  }

  // Consume the state: replacing the entry means refresh/back cannot show it again.
  useEffect(() => {
    if (incoming) {
      void navigate(`${location.pathname}${location.search}${location.hash}`, { replace: true, state: null });
    }
  }, [incoming, navigate, location.pathname, location.search, location.hash]);

  useEffect(() => {
    if (!shown) {
      return undefined;
    }
    const id = window.setTimeout(() => setShown(null), FLASH_DURATION_MS);
    return () => window.clearTimeout(id);
  }, [shown]);

  const flash = shown?.flash;
  const Icon = flash?.tone === 'info' ? Info : CheckCircle2;
  return (
    <div className="flash-region" role="status" aria-live="polite">
      {flash ? (
        <div className={`alert alert--${flash.tone} flash`}>
          <Icon size={22} aria-hidden="true" />
          <div className="alert__body">
            <div className="alert__title">{flash.title}</div>
            {flash.message ? <p>{flash.message}</p> : null}
          </div>
          <button type="button" className="btn btn--ghost btn--sm icon-btn flash__close" aria-label="Chiudi notifica" onClick={() => setShown(null)}>
            <X size={20} aria-hidden="true" />
          </button>
        </div>
      ) : null}
    </div>
  );
}

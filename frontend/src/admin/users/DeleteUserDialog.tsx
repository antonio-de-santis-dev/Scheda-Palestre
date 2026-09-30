import { useEffect, useId, useRef, useState } from 'react';
import { AlertTriangle, Trash2 } from 'lucide-react';
import { Button } from '../../shared/components/Button';
import { TextField } from '../../shared/components/Field';
import { ErrorAlert } from '../../shared/components/Alert';

interface DeleteUserDialogProps {
  open: boolean;
  username: string;
  pending: boolean;
  error: unknown;
  onConfirm: () => void;
  onCancel: () => void;
}

/**
 * Two distinct confirmations (ADR 0010). Step 1 explains the effects; step 2 requires typing the
 * username and the final button names the account. Native modal <dialog>: focus moves inside,
 * is trapped by the browser, Escape cancels and focus returns to the opening button.
 * Only the final destructive button is red; "Annulla" never is.
 */
export function DeleteUserDialog({ open, username, pending, error, onConfirm, onCancel }: DeleteUserDialogProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const [step, setStep] = useState<1 | 2>(1);
  const [typed, setTyped] = useState('');
  const [typedError, setTypedError] = useState<string | undefined>();
  const [wasOpen, setWasOpen] = useState(open);

  // Every opening starts again from the first confirmation.
  if (open !== wasOpen) {
    setWasOpen(open);
    if (open) {
      setStep(1);
      setTyped('');
      setTypedError(undefined);
    }
  }

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) {
      return;
    }
    if (open && !dialog.open) {
      if (typeof dialog.showModal === 'function') {
        dialog.showModal();
      } else {
        dialog.setAttribute('open', '');
      }
    } else if (!open && dialog.open) {
      if (typeof dialog.close === 'function') {
        dialog.close();
      } else {
        dialog.removeAttribute('open');
      }
    }
  }, [open]);

  const matches = typed.trim() === username;

  return (
    <dialog
      ref={ref}
      className="dialog"
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault();
        if (!pending) {
          onCancel();
        }
      }}
    >
      {open && step === 1 ? (
        <>
          <div className="dialog__body">
            <h2 id={titleId}>
              <AlertTriangle size={24} aria-hidden="true" style={{ verticalAlign: 'text-bottom' }} /> Eliminare l'account
              @{username}?
            </h2>
            <p>L'eliminazione non si può annullare. Ecco cosa succede, dato per dato:</p>
            <ul>
              <li>
                <strong>Accessi</strong>: le sessioni aperte vengono chiuse subito e non sarà più possibile entrare.
              </li>
              <li>
                <strong>Schede</strong>: le assegnazioni attive e in attesa vengono chiuse; un allenamento in corso viene
                interrotto.
              </li>
              <li>
                <strong>Dati personali</strong>: nome, cognome, username, email e telefono vengono sostituiti da valori
                anonimi.
              </li>
              <li>
                <strong>Storico</strong>: allenamenti, serie e assegnazioni restano conservati in forma anonima.
              </li>
            </ul>
          </div>
          <div className="dialog__actions">
            <Button variant="secondary" onClick={onCancel}>
              Annulla
            </Button>
            <Button variant="primary" onClick={() => setStep(2)}>
              Continua
            </Button>
          </div>
        </>
      ) : null}
      {open && step === 2 ? (
        <form
          noValidate
          onSubmit={(e) => {
            e.preventDefault();
            if (!matches) {
              setTypedError('Lo username non corrisponde');
              return;
            }
            onConfirm();
          }}
        >
          <div className="dialog__body">
            <h2 id={titleId}>Conferma definitiva</h2>
            {error ? <ErrorAlert error={error} /> : null}
            <TextField
              label={`Per confermare scrivi lo username: ${username}`}
              autoComplete="off"
              autoFocus
              value={typed}
              error={typedError}
              onChange={(e) => {
                setTyped(e.target.value);
                setTypedError(undefined);
              }}
            />
          </div>
          <div className="dialog__actions">
            <Button variant="secondary" onClick={onCancel} disabled={pending}>
              Annulla
            </Button>
            <Button type="submit" variant="danger" loading={pending} icon={<Trash2 size={18} aria-hidden="true" />}>
              Elimina definitivamente {username}
            </Button>
          </div>
        </form>
      ) : null}
    </dialog>
  );
}

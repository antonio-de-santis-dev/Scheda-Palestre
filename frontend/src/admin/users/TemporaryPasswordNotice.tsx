import { useState } from 'react';
import { Copy, KeyRound } from 'lucide-react';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';

interface TemporaryPasswordNoticeProps {
  username: string;
  password: string;
  onDismiss: () => void;
}

/** The temporary password is shown only once (US-25) and never stored client side. */
export function TemporaryPasswordNotice({ username, password, onDismiss }: TemporaryPasswordNoticeProps) {
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(password);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };

  return (
    <Alert tone="warning" title="Password temporanea: viene mostrata una sola volta">
      <p>
        Comunica a <strong>{username}</strong> questa password. Al primo accesso dovrà cambiarla.
      </p>
      <p className="row">
        <KeyRound size={18} aria-hidden="true" />
        <code style={{ fontSize: 'var(--text-xl)', fontWeight: 700, letterSpacing: '0.05em' }} data-testid="temporary-password">
          {password}
        </code>
      </p>
      <div className="row">
        <Button variant="secondary" size="sm" onClick={() => void copy()} icon={<Copy size={16} aria-hidden="true" />}>
          {copied ? 'Copiata' : 'Copia'}
        </Button>
        <Button variant="secondary" size="sm" onClick={onDismiss}>
          Ho comunicato la password
        </Button>
      </div>
      <span className="visually-hidden" aria-live="polite">
        {copied ? 'Password copiata negli appunti' : ''}
      </span>
    </Alert>
  );
}

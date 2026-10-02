import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { http } from '../shared/api/http';
import { Button } from '../shared/components/Button';
import { disablePush, enablePush, pushSupported } from './push';

export function PwaSettings() {
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const supported = pushSupported();
  const config = useQuery({ queryKey: ['me', 'push', 'config'], queryFn: () => http.get<{ enabled: boolean; publicKey: string | null }>('/api/me/push/config'), enabled: supported });
  const action = async (enable: boolean) => {
    setBusy(true); setMessage('');
    try {
      if (enable && config.data?.publicKey) await enablePush(config.data.publicKey); else await disablePush();
      setMessage(enable ? 'Notifiche attivate su questo dispositivo.' : 'Notifiche disattivate su questo dispositivo.');
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Impossibile aggiornare le notifiche. Riprova.'); }
    finally { setBusy(false); }
  };
  return <section className="card stack" aria-labelledby="pwa-title">
    <h2 id="pwa-title">App e notifiche</h2>
    <p>Puoi installare GymPlanner dal menu del browser. Su iPhone/iPad usa Condividi → Aggiungi alla schermata Home.</p>
    <p>Ricevi un avviso quando termina il recupero, anche con la pagina chiusa. Gli avvisi dipendono dalla connessione e dalle impostazioni del dispositivo.</p>
    {!supported ? <p>Notifiche non disponibili in questo browser. Su iPhone prova dall’app installata nella schermata Home.</p> : <>
      {config.isError ? <p>Configurazione non raggiungibile. <button className="btn btn--secondary" onClick={() => void config.refetch()}>Riprova</button></p> : config.data && !config.data.enabled ? <p>Le notifiche non sono ancora attive per questa palestra.</p> : null}
      <div className="row"><Button disabled={busy || !config.data?.enabled} onClick={() => void action(true)}>Attiva notifiche</Button>
        <Button variant="secondary" disabled={busy} onClick={() => void action(false)}>Disattiva notifiche</Button></div>
    </>}
    {message ? <p role="status">{message}</p> : null}
    <p className="small muted">Offline viene mostrata una pagina di riconnessione. Allenamenti e dati personali richiedono il server.</p>
  </section>;
}

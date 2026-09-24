# Test end-to-end (Playwright)

Coprono i flussi principali della specifica (§16), eseguiti su un sistema reale a 360 px:

1. ADMIN login → crea USER → USER primo accesso → cambio password
2. ADMIN crea catalogo → crea scheda → assegna
3. USER sceglie i giorni → vede l'allenamento di oggi
4. USER avvia → completa serie → timer → conclude
5. USER salta un esercizio → lo storico mostra "Saltato"
6. ADMIN modifica la scheda → lo storico passato resta invariato

## Esecuzione

1. Avvia PostgreSQL, backend e frontend come nel `README.md` (backend su 8080, Vite su 5173).
2. Installa il browser una sola volta: `npx playwright install chromium`.
3. Esegui:

```bash
cd frontend
npm run e2e
```

Variabili opzionali:

| Variabile | Default | Significato |
| --- | --- | --- |
| `E2E_BASE_URL` | `http://localhost:5173` | indirizzo del frontend |
| `E2E_ADMIN_USERNAME` | `admin` | `GYM_ADMIN_USERNAME` |
| `E2E_ADMIN_PASSWORD` | `Admin12345` | `GYM_ADMIN_PASSWORD` iniziale (usata solo su database nuovo) |
| `E2E_ADMIN_NEW_PASSWORD` | `AdminE2e2026` | password impostata dai test al primo accesso ADMIN |

Su un database nuovo i test eseguono anche il cambio password obbligatorio dell'ADMIN; nelle
esecuzioni successive usano `E2E_ADMIN_NEW_PASSWORD`. Ogni esecuzione crea dati con nomi univoci,
quindi la suite è ripetibile. Se hai già cambiato a mano la password dell'ADMIN, imposta
`E2E_ADMIN_NEW_PASSWORD` con la password attuale.

Report HTML: `npx playwright show-report`.

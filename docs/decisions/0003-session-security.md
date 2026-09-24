# ADR 0003 - Sicurezza: sessione server, CSRF per SPA, versione di sessione

**Problema.** §11 richiede sessione server con cookie HttpOnly/SameSite=Lax, CSRF attivo,
blocco dopo 5 tentativi, messaggio generico, invalidazione delle sessioni alla disattivazione
o al reset della password, cambio password obbligatorio al primo accesso.

**Alternative.** JWT in localStorage (scartato: esposto a XSS, invalidazione complessa);
`SessionRegistry` di Spring Security (richiede di registrare manualmente le sessioni del
login JSON); rivalidazione per richiesta con un contatore di versione.

**Decisione.**
- Login JSON (`POST /api/auth/login`) gestito da `AuthService`; il principal
  (`AuthenticatedUser`) è salvato nella `HttpSession` (`GYMSESSION`, HttpOnly, SameSite=Lax,
  Secure configurabile con `GYM_COOKIE_SECURE`), con rotazione dell'id di sessione al login.
- CSRF: `csrf.spa()` di Spring Security 7 (cookie `XSRF-TOKEN` letto dalla SPA e rinviato
  nell'header `X-XSRF-TOKEN`); `GET /api/auth/csrf` inizializza il cookie.
- Colonna tecnica `users.session_version`, incrementata a disattivazione, reset e cambio
  password. Un filtro (`SessionUserRefreshFilter`) confronta a ogni richiesta la versione del
  principal con il database: se differisce o l'account è disattivato la sessione viene
  invalidata (→ 401). Aggiorna inoltre ruolo e flag `mustChangePassword`.
- `PasswordChangeRequiredFilter`: con `mustChangePassword=true` sono ammessi solo login,
  logout, `me`, `csrf` e `change-password`; il resto risponde 403 `PASSWORD_CHANGE_REQUIRED`.
- BCrypt tramite `DelegatingPasswordEncoder`; confronto con hash fittizio per utenti
  inesistenti o bloccati (tempi simili, nessuna enumerazione).
- Regole URL: `/api/admin/**` → ADMIN; `/api/me/profile` → autenticato; altri `/api/me/**` →
  USER. La proprietà delle risorse è verificata nei servizi (404 per risorse altrui).

**Conseguenze.** Una query per PK a ogni richiesta autenticata (costo trascurabile). Il cambio
password invalida le altre sessioni dell'utente ma non quella corrente.

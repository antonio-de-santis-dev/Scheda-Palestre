# ADR 0009 - Durata consigliata della scheda (`expires_on`)

**Problema.** Le schede hanno una durata consigliata dopo la quale la palestra vuole proporne una
nuova. Serve un avviso per l'utente e un indicatore per l'ADMIN, senza bloccare niente.

**Decisione.**
- Si **riusa `workout_plans.expires_on`** (V3), senza aggiungere un campo equivalente. Significa
  "**fine della durata consigliata**" della scheda. È diverso da `plan_assignments.end_date`, che è
  la chiusura effettiva di un'assegnazione. Nell'interfaccia il campo si chiama "Fine durata
  consigliata".
- **Regola:** l'avviso compare **dal giorno successivo** alla data (`oggi > expires_on`, mai
  `>=`), solo per assegnazioni **attive**. La scheda resta pienamente utilizzabile: scelta dei
  giorni, avvio e completamento degli allenamenti funzionano normalmente.
- **Calcolo lato server** con `BusinessCalendar.today()`, cioè il fuso della palestra
  (`gymplanner.time-zone`, default `Europe/Rome`) e il `Clock` iniettato, che i test fissano con
  `MutableClock`. Il frontend non ricalcola mai: il fuso del browser può essere diverso.
- Esposizione:
  - `AssignmentResponse.planExpiresOn` e `recommendedDurationEnded`, in "Le mie schede" e nelle
    liste ADMIN;
  - `today.recommendedDurationEnded[]` con nome scheda e data;
  - `GET /api/admin/assignments/recommended-duration-ended` per l'indicatore nella lista utenti.
    Usa due query aggregate (schede scadute, poi le loro assegnazioni attive), mai una per utente.
- Nessun job schedulato e nessun cambio automatico di stato.

**Accessibilità.** L'avviso è un `role="status"` con icona e testo esplicito (nome scheda e
data), non solo colore. Non nasconde né blocca le azioni della scheda.

**Test.** `RecommendedDurationIntegrationTest` copre:
- giorno prima e giorno esatto: nessun avviso;
- giorno dopo: avviso, e allenamento avviato e completato comunque;
- `expires_on` nullo;
- assegnazione chiusa: non segnalata.

# ADR 0010 - Eliminazione utente (logica, con anonimizzazione) e report attività

## Mappa dei riferimenti a `users`

| Tabella | Colonna | Effetto di una cancellazione fisica |
| --- | --- | --- |
| `workout_plans` | `created_by` | FK senza cascata: bloccata (o schede orfane) |
| `plan_assignments` | `user_id`, `assigned_by` | FK senza cascata: storico assegnazioni perso |
| `workouts` (→ `workout_exercises`, `workout_sets`) | `user_id` | FK senza cascata: storico allenamenti perso |
| Sessioni HTTP | `users.session_version` | restano in memoria finché scadono |

Non esistono tabelle di audit separate.

## Decisione: eliminazione logica con anonimizzazione

- `V9__user_logical_deletion.sql` aggiunge `users.deleted_at`. Il vincolo
  `ck_users_deleted_inactive` impone che un account eliminato non sia mai attivo.
- `DELETE /api/admin/users/{id}`, riservato all'ADMIN e verificato lato server, esegue in una sola
  transazione:
  1. anonimizza i dati personali con segnaposto deterministici e unici per id: `Utente eliminato`,
     `deleted-<id>`, `deleted-<id>@deleted.invalid`, telefono nullo;
  2. imposta una password inutilizzabile, `active=false` e `deleted_at`;
  3. incrementa `session_version`, così le sessioni aperte vengono chiuse alla richiesta
     successiva;
  4. pubblica `UserEvents.UserDeleted`: il modulo assignment chiude le assegnazioni attive e in
     attesa, e il modulo execution interrompe l'allenamento in corso tramite `AssignmentClosed`.
- Tutte le righe collegate restano: lo storico è conservato in forma anonima.
- Il login di un account eliminato risponde `INVALID_CREDENTIALS`, come per credenziali sbagliate,
  quindi non rivela l'eliminazione.
- **Rifiuti:**
  - il proprio account: `CANNOT_DELETE_SELF`;
  - l'ADMIN iniziale di configurazione (`GYM_ADMIN_USERNAME`): `PROTECTED_ACCOUNT`;
  - l'ultimo ADMIN attivo: `LAST_ACTIVE_ADMIN`. Questa è una rete di sicurezza: con il divieto di
    auto-eliminazione non è raggiungibile via API.
- **Idempotenza:** rispondono `204` sia la prima richiesta sia le successive; `404` se l'id non è
  mai esistito.
- Un account eliminato:
  - è nascosto dall'elenco utenti;
  - è in sola lettura (`ACCOUNT_DELETED` su modifica, riattivazione e reset password);
  - non può ricevere schede (`USER_NOT_ASSIGNABLE`).

**Interfaccia.**
- Una zona distruttiva separata dai pulsanti di salvataggio, con due conferme distinte:
  1. la prima spiega l'effetto, dato per dato;
  2. la seconda chiede di scrivere lo username; il pulsante finale dice
     "Elimina definitivamente *username*".
- Solo l'azione finale è rossa.
- Il dialog è un `<dialog>` modale nativo: focus dentro, trappola, Esc, ritorno del focus.

## Report attività

`GET /api/admin/users/{id}/activity-report` (modulo execution) contiene:
- per scheda e in totale: allenamenti completati, interrotti e in corso; serie completate;
  esercizi completati e saltati; sessioni diverse svolte rispetto a quelle della scheda; giorni
  scelti; stato; durata consigliata;
- l'andamento delle ultime 12 settimane ISO, comprese quelle a zero.

Scelte:
- Solo query aggregate (`ReportRepository`, numero fisso di query), `readOnly`.
- **Solo dati registrati:** l'app non registra carichi, peso corporeo o progressi fisici e il
  report non li inventa.
- L'interfaccia separa "Dati registrati" (tabelle con `caption` e `th scope`) da "Lettura dei
  dati", etichettata come interpretazione (percentuale di allenamenti portati a termine,
  settimane attive).
- Nei grafici a barre il valore è sempre scritto accanto alla barra.
- Senza dati il report mostra "Nessun allenamento registrato.", non una serie di zeri ambigui.

**Architettura.** Il report deve verificare che l'utente esista (404), quindi `execution` può
leggere `identity.api`. `ArchitectureTest` è aggiornato e la dipendenza resta aciclica.

# ADR 0002 - Confini dei moduli: riferimenti per id ed eventi di dominio

**Problema.** La specifica (§6.1) impone un monolite modulare in cui ogni modulo espone solo il
package `api`, non importa gli `internal` degli altri e rispetta le dipendenze
`workoutplan→catalog`, `assignment→identity,workoutplan`, `calendar→assignment,workoutplan`,
`execution→calendar,assignment,workoutplan`. Alcune regole di business però vanno "all'indietro":
eliminare una scheda chiude le assegnazioni (§US-08), chiudere un'assegnazione interrompe
l'allenamento in corso (§10.5), modificare le sessioni ri-ancora la rotazione (§10.6).

**Alternative.**
1. Associazioni JPA fra entità di moduli diversi → violano l'incapsulamento degli `internal`.
2. Chiamate dirette "all'indietro" → creano cicli e violano le dipendenze ammesse.
3. Riferimenti per id + eventi di dominio sincroni pubblicati dal modulo "a monte" e gestiti
   dal modulo "a valle" nella stessa transazione.

**Decisione.** Opzione 3.
- Le tabelle mantengono le foreign key reali (integrità in PostgreSQL), ma nel codice le entità
  di moduli diversi si riferiscono per `UUID`.
- Ogni modulo espone servizi/DTO/eventi in `api`; entità, repository, controller in `internal`.
- Gli eventi (`record` in `api`) sono pubblicati con `ApplicationEventPublisher` e gestiti con
  `@EventListener` sincrono: stessa transazione, quindi atomicità (se il listener fallisce,
  l'operazione originale fa rollback).
- Le viste che combinano rotazione e allenamenti (`/api/me/today`, `/api/me/calendar`) sono
  orchestrate in `execution`, l'unico modulo che può vedere sia `calendar` sia gli allenamenti.
- Le verifiche sono automatiche (`ArchitectureTest`, ArchUnit).

**Conseguenze.** Per evitare N+1 i nomi dei cataloghi e degli utenti vengono caricati in blocco
tramite le API dei moduli proprietari. Nessun `@ManyToMany` fra utente e scheda:
`PlanAssignment` è un'entità vera.

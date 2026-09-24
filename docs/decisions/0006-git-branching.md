# ADR 0006 - Strategia Git e branch

**Decisione.**
- `main`: solo codice verificato. Viene aggiornato solo dal merge finale di `develop`.
- `develop`: integrazione, creato da `main`.
- Un branch per incremento o area, creato da `develop` e reintegrato con `merge --no-ff`
  dopo test e build verdi: `chore/increment-0-foundation`, `feat/identity-accounts`,
  `feat/catalog-management`, `feat/workout-plans`, `feat/plan-assignments`,
  `feat/calendar-rotation`, `feat/workout-execution`, `feat/workout-history`,
  `test/integration-e2e`, `docs/final-documentation`.
- Frontend e backend di una user story vivono nello stesso branch (slice verticale), così ogni
  merge in `develop` è una funzionalità completa e verificabile.
- Conventional Commits; nessun force-push; nessuna modifica a migrazioni Flyway già pubblicate.

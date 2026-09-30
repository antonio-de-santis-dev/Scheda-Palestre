# ADR 0007 - Ogni esercizio appartiene a un solo gruppo muscolare

**Problema.** Fino a V6 gruppi muscolari ed esercizi erano due cataloghi indipendenti: nell'editor
si poteva mettere "Squat" nella sezione "Petto". Il nuovo requisito chiede un catalogo unico in cui
ogni esercizio appartiene a **un solo** gruppo e l'editor propone solo gli esercizi del gruppo della
sezione.

**Alternative.**
1. Tabella ponte esercizio↔gruppo (molti-a-molti): più flessibile, ma contraddice il requisito
   "un solo gruppo" e complica l'editor.
2. Colonna `exercises.muscle_group_id` (uno-a-molti) — **scelta**.

**Decisione.**
- `V7__exercise_muscle_group.sql` aggiunge `exercises.muscle_group_id` con FK e indice, senza
  cancellare nulla, in quattro passi:
  1. colonna nullable;
  2. gli esercizi già usati nelle schede ricevono il gruppo delle sezioni in cui compaiono
     (`plan_exercises` → `muscle_sections.muscle_group_id`). Se un esercizio compare sotto più gruppi
     vince il **più usato**; a parità, il nome del gruppo in ordine alfabetico, poi l'id
     (deterministico e ripetibile);
  3. gli esercizi mai usati vanno nel gruppo **"Senza gruppo (storico)"** con id fisso
     `00000000-0000-4000-8000-00000000c0de`, creato **disattivato** e solo se serve (se un ADMIN
     aveva già un gruppo con quel nome, viene riusato);
  4. `NOT NULL`: i passi 2-3 coprono il 100% delle righe per costruzione.
- API: `POST/PUT /api/admin/exercises` accettano `muscleGroupId` (obbligatorio in creazione,
  facoltativo in modifica = sposta l'esercizio); `GET /api/admin/exercises?muscleGroupId=` filtra;
  i gruppi espongono `exerciseCount` e `activeExerciseCount` (una sola query aggregata).
- Un esercizio nuovo o spostato può andare solo in un gruppo **attivo**.
- Validazione lato server nell'editor delle schede: aggiungere un esercizio a una sezione, o
  cambiare l'esercizio di una riga, richiede un esercizio **attivo** dello **stesso gruppo** della
  sezione, altrimenti `422 EXERCISE_GROUP_MISMATCH` con `exerciseId`, `exerciseName`,
  `muscleGroupId`, `muscleGroupName`, `exerciseMuscleGroupId` nel Problem Details.
- **Dati storici tollerati.** Le righe già presenti che puntano a un esercizio disattivato o spostato
  in un altro gruppo restano **valide e salvabili** (si può modificare serie/ripetizioni mantenendo
  lo stesso esercizio). La struttura della scheda le segnala con
  `exerciseInSectionGroup = false` e l'editor mostra "ora in un altro gruppo". La validazione stretta
  si applica solo agli elementi aggiunti o cambiati nella richiesta.
- **Snapshot intoccati.** L'esecuzione legge nomi e valori dallo snapshot salvato all'avvio
  (`workout_exercises.exercise_name_snapshot`, `muscle_group_name_snapshot`, `workout_sets`): la
  migrazione e gli spostamenti di catalogo non cambiano lo storico. Verificato da
  `ExerciseGroupMigrationTest` (database V6 con dati → V7).

**Interfaccia.** Un'unica pagina `/admin/catalog` (master-detail da 900 px, drill-down su telefono,
gruppo selezionato in `?group=`). I vecchi URL `/admin/catalog/muscle-groups` e
`/admin/catalog/exercises` reindirizzano alla nuova pagina; nginx serve `index.html` per ogni rotta
(`try_files`), quindi i deep link funzionano anche nel profilo Docker `app`. La scelta di gruppo ed
esercizio nell'editor usa una combobox ARIA (`shared/components/Combobox.tsx`).

**Conseguenze.** Il nome dell'esercizio resta unico nell'intero catalogo (indice V2 invariato).
Disattivare un gruppo non sposta i suoi esercizi: restano nel gruppo ma il gruppo non si può usare
per nuove sezioni né per nuovi esercizi.

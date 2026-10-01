package com.gymplanner.catalog.internal;

import com.gymplanner.shared.error.NotFoundException;
import java.time.Clock;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CatalogDeletionService {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    CatalogDeletionService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public void deleteExercise(UUID id) {
        Map<String, Object> params = Map.of("id", id);

        List<UUID> existing = jdbc.queryForList("""
                SELECT id
                FROM exercises
                WHERE id = :id
                FOR UPDATE
                """, params, UUID.class);

        if (existing.isEmpty()) {
            throw new NotFoundException("Exercise");
        }

        List<UUID> affectedPlans = jdbc.queryForList("""
                SELECT wp.id
                FROM workout_plans wp
                WHERE wp.id IN (
                    SELECT ps.workout_plan_id
                    FROM plan_sessions ps
                    JOIN muscle_sections ms
                        ON ms.plan_session_id = ps.id
                    JOIN plan_exercises pe
                        ON pe.muscle_section_id = ms.id
                    WHERE pe.exercise_id = :id
                )
                ORDER BY wp.id
                FOR UPDATE
                """, params, UUID.class);

        jdbc.update("""
                DELETE FROM plan_exercises
                WHERE exercise_id = :id
                """, params);

        jdbc.update("""
                DELETE FROM exercises
                WHERE id = :id
                """, params);

        updatePlans(affectedPlans);
    }

    @Transactional
    public void deleteGroup(UUID id) {
        Map<String, Object> params = Map.of("id", id);

        List<UUID> existing = jdbc.queryForList("""
                SELECT id
                FROM muscle_groups
                WHERE id = :id
                FOR UPDATE
                """, params, UUID.class);

        if (existing.isEmpty()) {
            throw new NotFoundException("Muscle group");
        }

        // Blocca gli esercizi prima di rimuoverne i collegamenti.
        jdbc.queryForList("""
                SELECT id
                FROM exercises
                WHERE muscle_group_id = :id
                ORDER BY id
                FOR UPDATE
                """, params, UUID.class);

        // Include anche esercizi utilizzati sotto un gruppo diverso
        // nelle schede, prima di un eventuale spostamento nel catalogo.
        List<UUID> affectedPlans = jdbc.queryForList("""
                SELECT wp.id
                FROM workout_plans wp
                WHERE wp.id IN (
                    SELECT ps.workout_plan_id
                    FROM plan_sessions ps
                    JOIN muscle_sections ms
                        ON ms.plan_session_id = ps.id
                    LEFT JOIN plan_exercises pe
                        ON pe.muscle_section_id = ms.id
                    LEFT JOIN exercises e
                        ON e.id = pe.exercise_id
                    WHERE ms.muscle_group_id = :id
                       OR e.muscle_group_id = :id
                )
                ORDER BY wp.id
                FOR UPDATE
                """, params, UUID.class);

        jdbc.update("""
                DELETE FROM plan_exercises
                WHERE exercise_id IN (
                    SELECT id
                    FROM exercises
                    WHERE muscle_group_id = :id
                )
                """, params);

        // Le FK esistenti eliminano anche esercizi e serie
        // appartenenti alle sezioni rimosse.
        jdbc.update("""
                DELETE FROM muscle_sections
                WHERE muscle_group_id = :id
                """, params);

        jdbc.update("""
                DELETE FROM exercises
                WHERE muscle_group_id = :id
                """, params);

        jdbc.update("""
                DELETE FROM muscle_groups
                WHERE id = :id
                """, params);

        updatePlans(affectedPlans);
    }

    private void updatePlans(List<UUID> planIds) {
        for (UUID planId : planIds) {
            Map<String, Object> params = Map.of(
                    "planId", planId,
                    "updatedAt", Timestamp.from(clock.instant()));

            // Mantiene consecutive le posizioni degli esercizi.
            jdbc.update("""
                    WITH ordered AS (
                        SELECT pe.id,
                               row_number() OVER (
                                   PARTITION BY pe.muscle_section_id
                                   ORDER BY pe.position, pe.id
                               )::integer AS new_position
                        FROM plan_exercises pe
                        JOIN muscle_sections ms
                            ON ms.id = pe.muscle_section_id
                        JOIN plan_sessions ps
                            ON ps.id = ms.plan_session_id
                        WHERE ps.workout_plan_id = :planId
                    )
                    UPDATE plan_exercises pe
                    SET position = ordered.new_position
                    FROM ordered
                    WHERE pe.id = ordered.id
                      AND pe.position <> ordered.new_position
                    """, params);

            // Mantiene consecutive le posizioni delle sezioni.
            jdbc.update("""
                    WITH ordered AS (
                        SELECT ms.id,
                               row_number() OVER (
                                   PARTITION BY ms.plan_session_id
                                   ORDER BY ms.position, ms.id
                               )::integer AS new_position
                        FROM muscle_sections ms
                        JOIN plan_sessions ps
                            ON ps.id = ms.plan_session_id
                        WHERE ps.workout_plan_id = :planId
                    )
                    UPDATE muscle_sections ms
                    SET position = ordered.new_position
                    FROM ordered
                    WHERE ms.id = ordered.id
                      AND ms.position <> ordered.new_position
                    """, params);

            // Segnala la modifica anche agli editor già aperti.
            jdbc.update("""
                    UPDATE workout_plans
                    SET version = version + 1,
                        updated_at = :updatedAt
                    WHERE id = :planId
                    """, params);
        }
    }
}

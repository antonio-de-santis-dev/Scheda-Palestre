package com.gymplanner.workoutplan.internal;

import com.gymplanner.shared.error.BadRequestException;
import com.gymplanner.shared.error.FieldViolation;
import com.gymplanner.workoutplan.internal.PlanDtos.PlanExerciseRequest;
import com.gymplanner.workoutplan.internal.PlanDtos.SetRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Cross-field rules of spec 8.8/8.9 that Bean Validation cannot express:
 * <ul>
 *   <li>to failure ("MAX") means reps = 0, otherwise reps between 1 and 100;</li>
 *   <li>custom sets are all-or-nothing: none, or exactly {@code setsCount} rows with indexes 1..N.</li>
 * </ul>
 */
final class ExerciseConfigValidator {

    private ExerciseConfigValidator() {
    }

    static ExerciseConfig validate(PlanExerciseRequest request) {
        List<FieldViolation> errors = new ArrayList<>();
        checkReps("reps", request.reps(), request.toFailure(), errors);

        List<SetRequest> custom = request.customSets() == null ? List.of() : request.customSets();
        List<ExerciseConfig.SetConfig> sets = new ArrayList<>();
        if (!custom.isEmpty()) {
            List<SetRequest> sorted = custom.stream().sorted(Comparator.comparingInt(SetRequest::setIndex)).toList();
            boolean complete = sorted.size() == request.setsCount();
            for (int i = 0; complete && i < sorted.size(); i++) {
                complete = sorted.get(i).setIndex() == i + 1;
            }
            if (!complete) {
                throw new BadRequestException("INVALID_CUSTOM_SETS",
                        "Custom sets must cover every set from 1 to setsCount exactly once",
                        List.of(new FieldViolation("customSets", "Custom sets must cover every set from 1 to "
                                + request.setsCount() + " exactly once")));
            }
            for (SetRequest s : sorted) {
                checkReps("customSets[" + (s.setIndex() - 1) + "].reps", s.reps(), s.toFailure(), errors);
                sets.add(new ExerciseConfig.SetConfig(s.setIndex(), s.reps(), s.toFailure(), s.restSeconds()));
            }
        }
        if (!errors.isEmpty()) {
            throw new BadRequestException("VALIDATION_ERROR", "One or more fields are invalid", errors);
        }
        return new ExerciseConfig(request.setsCount(), request.reps(), request.toFailure(), request.restSeconds(),
                List.copyOf(sets));
    }

    private static void checkReps(String field, int reps, boolean toFailure, List<FieldViolation> errors) {
        if (toFailure && reps != 0) {
            errors.add(new FieldViolation(field, "Reps must be 0 when the set is to failure (MAX)"));
        } else if (!toFailure && (reps < 1 || reps > 100)) {
            errors.add(new FieldViolation(field, "Reps must be between 1 and 100"));
        }
    }
}

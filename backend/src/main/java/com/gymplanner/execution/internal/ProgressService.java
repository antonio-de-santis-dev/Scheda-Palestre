package com.gymplanner.execution.internal;

import com.gymplanner.shared.error.BadRequestException;
import com.gymplanner.shared.time.BusinessCalendar;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class ProgressService {
    record Day(LocalDate date, long completed, long interrupted, long inProgress, long durationSeconds, long closedWorkouts) {}
    record ExerciseDay(String key, String name, String muscleGroup, LocalDate date, long completedSets,
            long volumeSets, BigDecimal volumeKg, BigDecimal maxWeightKg, Integer maxReps) {}
    record PersonalRecord(String key, String name, String muscleGroup, BigDecimal maxWeightKg, Integer maxReps) {}
    record Progress(LocalDate from, LocalDate to, LocalDate generatedOn, List<Day> days,
            List<ExerciseDay> exerciseDays, List<PersonalRecord> records) {}

    private final ProgressRepository repository;
    private final BusinessCalendar calendar;

    @Transactional(readOnly = true)
    public Progress progress(UUID userId, LocalDate from, LocalDate to) {
        LocalDate end = to == null ? calendar.today() : to;
        LocalDate start = from == null ? end.minusDays(83) : from;
        if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) > 365) {
            throw BadRequestException.field("to", "Range must contain 1 to 366 days");
        }
        List<Day> days = repository.days(userId, start, end).stream()
                .map(r -> new Day(date(r[0]), number(r[1]), number(r[2]), number(r[3]), number(r[4]), number(r[5]))).toList();
        List<ExerciseDay> exerciseDays = repository.exerciseDays(userId, start, end).stream()
                .map(r -> new ExerciseDay((String) r[0], (String) r[1], (String) r[2], date(r[3]), number(r[4]),
                        number(r[5]), decimal(r[6]), decimal(r[7]), integer(r[8]))).toList();
        List<PersonalRecord> records = repository.records(userId).stream()
                .map(r -> new PersonalRecord((String) r[0], (String) r[1], (String) r[2], decimal(r[3]), integer(r[4]))).toList();
        return new Progress(start, end, calendar.today(), days, exerciseDays, records);
    }

    private static LocalDate date(Object value) {
        return value instanceof java.sql.Date d ? d.toLocalDate() : (LocalDate) value;
    }
    private static long number(Object value) { return ((Number) value).longValue(); }
    private static Integer integer(Object value) { return value == null ? null : ((Number) value).intValue(); }
    private static BigDecimal decimal(Object value) { return value == null ? null : new BigDecimal(value.toString()); }
}

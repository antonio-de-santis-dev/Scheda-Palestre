import type { PlanSession } from '../api/planTypes';
import { describeSets } from '../api/planTypes';
import { formatWeight } from '../utils/weight';
import { restText } from '../utils/format';

/** Read-only rendering of a session: muscle sections, exercises and planned values. */
export function PlanSessionView({ session, headingLevel = 2, hideTitle = false }: { session: PlanSession; headingLevel?: 2 | 3; hideTitle?: boolean }) {
  const Heading = headingLevel === 2 ? 'h2' : 'h3';
  let exerciseNumber = 0;
  return (
    <article className="plan-session plan-session--readonly" aria-label={session.title}>
      {!hideTitle ? <header className="plan-session__header">
        <Heading>{session.title}</Heading>
      </header> : null}
      <div className="plan-session__body">
        {session.sections.length === 0 ? <p className="muted">Sessione ancora vuota.</p> : null}
        {session.sections.map((section) => (
          <section key={section.id} className="plan-section" aria-label={section.muscleGroupName}>
            <div className="plan-section__title">
              <span>{section.muscleGroupName}</span>
            </div>
            <ul className="list" style={{ gap: 0 }}>
              {section.exercises.map((exercise) => (
                <li key={exercise.id} className="exercise-row">
                  <span className="exercise-row__number" aria-hidden="true">{++exerciseNumber}</span>
                  <span className="exercise-row__name">{exercise.exerciseName}</span>
                  <span className="exercise-row__values">
                    {describeSets(exercise)}
                    {exercise.customized ? ` (${exercise.setsCount} serie)` : ''} · {restText(exercise.restSeconds)}
                    {exercise.customized
                      ? ` · Carichi: ${exercise.sets.map((s) => formatWeight(s.plannedWeightKg)).join(' / ')}`
                      : ` · ${formatWeight(exercise.plannedWeightKg)}`}
                  </span>
                </li>
              ))}
            </ul>
          </section>
        ))}
      </div>
    </article>
  );
}

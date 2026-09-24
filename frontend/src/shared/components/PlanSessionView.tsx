import type { PlanSession } from '../api/planTypes';
import { describeSets } from '../api/planTypes';
import { formatRest } from '../utils/format';

/** Read-only rendering of a session: muscle sections, exercises and planned values. */
export function PlanSessionView({ session, headingLevel = 2 }: { session: PlanSession; headingLevel?: 2 | 3 }) {
  const Heading = headingLevel === 2 ? 'h2' : 'h3';
  return (
    <article className="plan-session" aria-label={session.title}>
      <header className="plan-session__header">
        <Heading>{session.title}</Heading>
      </header>
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
                  <span className="exercise-row__name">{exercise.exerciseName}</span>
                  <span className="exercise-row__values">
                    {describeSets(exercise)}
                    {exercise.customized ? ` (${exercise.setsCount} serie)` : ''} · recupero {formatRest(exercise.restSeconds)}
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

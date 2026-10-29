import type { PlanSession } from '../api/planTypes';

interface SessionTabsProps {
  sessions: PlanSession[];
  activeId: string;
  onSelect: (id: string) => void;
  prefix: string;
}

/** Roving tab focus: Arrow keys, Home and End work without extra tab stops. */
export function SessionTabs({ sessions, activeId, onSelect, prefix }: SessionTabsProps) {
  return <div className="session-tabs" role="tablist" aria-label="Sessioni della scheda">
    {sessions.map((session, index) => <button key={session.id} type="button" role="tab"
      className="btn btn--secondary" id={`${prefix}-tab-${session.id}`} aria-controls={`${prefix}-panel-${session.id}`}
      aria-selected={activeId === session.id} tabIndex={activeId === session.id ? 0 : -1}
      onClick={() => onSelect(session.id)} onKeyDown={(event) => {
        let next: number;
        if (event.key === 'ArrowRight') next = (index + 1) % sessions.length;
        else if (event.key === 'ArrowLeft') next = (index - 1 + sessions.length) % sessions.length;
        else if (event.key === 'Home') next = 0;
        else if (event.key === 'End') next = sessions.length - 1;
        else return;
        event.preventDefault();
        const target = sessions[next]!;
        onSelect(target.id);
        document.getElementById(`${prefix}-tab-${target.id}`)?.focus();
      }}>
      {index < 26 ? `Sessione ${String.fromCharCode(65 + index)}` : `Sessione ${index + 1}`}
      <span className="visually-hidden">: {session.title}</span>
    </button>)}
  </div>;
}

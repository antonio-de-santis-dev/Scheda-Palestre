import type { ComponentType } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router';
import { LogOut } from 'lucide-react';
import { useCurrentUser, useLogout } from '../../auth/useAuth';
import { BrandMark } from './BrandMark';

export interface NavItem {
  to: string;
  label: string;
  icon: ComponentType<{ size?: number; 'aria-hidden'?: boolean }>;
  end?: boolean;
  /** Shown in the mobile bottom bar (max 5 items). */
  mobile?: boolean;
}

interface AppLayoutProps {
  home: string;
  areaLabel: string;
  items: NavItem[];
}

/** Shell shared by ADMIN and USER areas: top bar (desktop nav) + bottom bar on mobile. */
export function AppLayout({ home, areaLabel, items }: AppLayoutProps) {
  const { data: user } = useCurrentUser();
  const logout = useLogout();
  const navigate = useNavigate();
  const mobileItems = items.filter((i) => i.mobile !== false).slice(0, 5);

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main">
        Vai al contenuto
      </a>
      <header className="topbar">
        <Link to={home} className="topbar__brand">
          <BrandMark />
          <span>GymPlanner</span>
          <span className="visually-hidden">- {areaLabel}</span>
        </Link>
        <nav className="topnav" aria-label={`Navigazione ${areaLabel}`}>
          {items.map(({ to, label, icon: Icon, end }) => (
            <NavLink key={to} to={to} end={end} className="nav-link">
              <Icon size={18} aria-hidden={true} />
              {label}
            </NavLink>
          ))}
        </nav>
        {user ? (
          <span className="topbar__user">
            {user.firstName} {user.lastName}
          </span>
        ) : null}
        <button
          type="button"
          className="nav-link"
          style={{ background: 'transparent', border: 'none', font: 'inherit', cursor: 'pointer' }}
          onClick={() => logout.mutate(undefined, { onSettled: () => navigate('/login', { replace: true }) })}
          disabled={logout.isPending}
        >
          <LogOut size={18} aria-hidden="true" />
          Esci
        </button>
      </header>
      <main id="main" className="main" tabIndex={-1}>
        <Outlet />
      </main>
      {mobileItems.length > 1 ? (
        <nav className="bottomnav" aria-label={`Navigazione rapida ${areaLabel}`}>
          {mobileItems.map(({ to, label, icon: Icon, end }) => (
            <NavLink key={to} to={to} end={end} className="nav-link">
              <Icon size={22} aria-hidden={true} />
              {label}
            </NavLink>
          ))}
        </nav>
      ) : null}
    </div>
  );
}

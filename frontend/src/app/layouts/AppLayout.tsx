import { useLayoutEffect, useRef, useState, type ComponentType } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router';
import { LogOut, Menu, X } from 'lucide-react';
import { useCurrentUser, useLogout } from '../../auth/useAuth';
import { BrandMark } from './BrandMark';
import { FlashOutlet } from '../../shared/flash/FlashOutlet';
import { ThemeToggle } from '../../shared/components/ThemeToggle';

export interface NavItem {
  to: string;
  label: string;
  icon: ComponentType<{ size?: number; 'aria-hidden'?: boolean }>;
  end?: boolean;
  mobile?: boolean;
}

interface AppLayoutProps {
  home: string;
  areaLabel: string;
  items: NavItem[];
}

/** Desktop drawer and mobile bottom navigation share routes and active states. */
export function AppLayout({ home, areaLabel, items }: AppLayoutProps) {
  const { data: user } = useCurrentUser();
  const logout = useLogout();
  const navigate = useNavigate();
  const { pathname, hash } = useLocation();
  const isAdmin = pathname.startsWith('/admin');
  const drawer = useRef<HTMLDialogElement>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const profile = isAdmin ? '/admin/profile' : '/app/profile';
  const initials = `${user?.firstName.charAt(0) ?? ''}${user?.lastName.charAt(0) ?? ''}`;
  const closeMenu = () => drawer.current?.close();

  useLayoutEffect(() => {
    drawer.current?.close();
    if (!hash) window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
  }, [pathname, hash]);

  const mobileItems = items.filter((item) => item.mobile !== false).slice(0, 5);
  const signOut = () => logout.mutate(undefined, { onSettled: () => navigate('/login', { replace: true }) });

  return (
    <div className={`app-shell${isAdmin ? ' app-shell--admin' : ''}`}>
      <a className="skip-link" href="#main">Vai al contenuto</a>
      <header className="topbar">
        <button type="button" className="menu-trigger" aria-label="Apri menu" aria-controls="app-navigation"
          aria-expanded={menuOpen} onClick={() => { drawer.current?.showModal(); setMenuOpen(true); }}>
          <Menu size={24} aria-hidden="true" />
        </button>
        <Link to={home} className="topbar__brand"><BrandMark /><span>GymPlanner</span>
          <span className="visually-hidden">- {areaLabel}</span>
        </Link>
        <div className="topbar__account">
          <ThemeToggle />
          {user ? <Link to={profile} className="avatar" aria-label={`Profilo di ${user.firstName} ${user.lastName}`}>{initials}</Link> : null}
        </div>
      </header>
      <dialog ref={drawer} id="app-navigation" className="navigation-drawer" aria-label={`Menu ${areaLabel}`}
        onClose={() => setMenuOpen(false)} onCancel={() => setMenuOpen(false)} onClick={(event) => {
          if (event.target !== event.currentTarget) return;
          const bounds = event.currentTarget.getBoundingClientRect();
          if (event.clientX > bounds.right || event.clientY > bounds.bottom) closeMenu();
        }}>
        <div className="drawer-brand">
          <Link to={home} className="topbar__brand" onClick={closeMenu}><BrandMark /><span>GymPlanner</span></Link>
          <button type="button" className="theme-toggle" aria-label="Chiudi menu" onClick={closeMenu}><X size={22} aria-hidden="true" /></button>
        </div>
        <nav className="drawer-nav" aria-label={`Navigazione ${areaLabel}`}>
          {items.map(({ to, label, icon: Icon, end }) => <NavLink key={to} to={to} end={end} className="nav-link" onClick={closeMenu}>
            <Icon size={22} aria-hidden={true} /><span>{label}</span>
          </NavLink>)}
        </nav>
        <div className="drawer-account">
          <div className="row"><span className="avatar" aria-hidden="true">{initials}</span>
            <div><strong>{user?.firstName} {user?.lastName}</strong><div className="small muted">{areaLabel}</div></div>
          </div>
          <button type="button" className="nav-link" onClick={signOut} disabled={logout.isPending}><LogOut size={20} aria-hidden="true" />Esci</button>
        </div>
      </dialog>
      <main id="main" className="main" tabIndex={-1}><FlashOutlet /><Outlet /></main>
      <nav className="bottomnav" aria-label={`Navigazione rapida ${areaLabel}`}>
        {mobileItems.map(({ to, label, icon: Icon, end }) => <NavLink key={to} to={to} end={end} className="nav-link">
          <Icon size={22} aria-hidden={true} /><span>{label}</span>
        </NavLink>)}
      </nav>
    </div>
  );
}

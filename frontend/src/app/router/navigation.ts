import { CalendarCheck, CalendarDays, ClipboardList, Dumbbell, History, LayoutDashboard, Sun, Target, UserRound, Users } from 'lucide-react';
import type { ComponentType } from 'react';
import type { NavItem } from '../layouts/AppLayout';

export const ADMIN_NAV: NavItem[] = [
  { to: '/admin', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/admin/users', label: 'Utenti', icon: Users },
  { to: '/admin/catalog/muscle-groups', label: 'Gruppi', icon: Target },
  { to: '/admin/catalog/exercises', label: 'Esercizi', icon: Dumbbell },
  { to: '/admin/plans', label: 'Schede', icon: ClipboardList },
  { to: '/admin/profile', label: 'Profilo', icon: UserRound, mobile: false },
];

export const USER_NAV: NavItem[] = [
  { to: '/app/today', label: 'Oggi', icon: Sun },
  { to: '/app/calendar', label: 'Calendario', icon: CalendarDays },
  { to: '/app/plans', label: 'Schede', icon: ClipboardList },
  { to: '/app/schedule', label: 'Giorni', icon: CalendarCheck, mobile: false },
  { to: '/app/history', label: 'Storico', icon: History },
  { to: '/app/profile', label: 'Profilo', icon: UserRound },
];

export interface Shortcut {
  to: string;
  label: string;
  description: string;
  icon: ComponentType<{ size?: number; 'aria-hidden'?: boolean }>;
}

export const ADMIN_SHORTCUTS: Shortcut[] = [
  { to: '/admin/plans', label: 'Schede', description: 'Crea, modifica, duplica e assegna le schede.', icon: ClipboardList },
  { to: '/admin/users', label: 'Utenti', description: 'Crea account, disattiva, reset password.', icon: Users },
  { to: '/admin/catalog/muscle-groups', label: 'Gruppi muscolari', description: 'Catalogo dei gruppi muscolari.', icon: Target },
  { to: '/admin/catalog/exercises', label: 'Esercizi', description: 'Catalogo degli esercizi.', icon: Dumbbell },
  { to: '/admin/profile', label: 'Profilo', description: 'Telefono e cambio password.', icon: UserRound },
];

import { ClipboardList, Dumbbell, LayoutDashboard, Target, Users } from 'lucide-react';
import type { ComponentType } from 'react';
import type { NavItem } from '../layouts/AppLayout';

export const ADMIN_NAV: NavItem[] = [
  { to: '/admin', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/admin/users', label: 'Utenti', icon: Users },
  { to: '/admin/catalog/muscle-groups', label: 'Gruppi', icon: Target },
  { to: '/admin/catalog/exercises', label: 'Esercizi', icon: Dumbbell },
  { to: '/admin/plans', label: 'Schede', icon: ClipboardList },
];

export const USER_NAV: NavItem[] = [{ to: '/app', label: 'Home', icon: LayoutDashboard, end: true }];

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
];

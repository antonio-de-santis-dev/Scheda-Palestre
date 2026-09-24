import { LayoutDashboard, Users } from 'lucide-react';
import type { ComponentType } from 'react';
import type { NavItem } from '../layouts/AppLayout';

export const ADMIN_NAV: NavItem[] = [
  { to: '/admin', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/admin/users', label: 'Utenti', icon: Users },
];

export const USER_NAV: NavItem[] = [{ to: '/app', label: 'Home', icon: LayoutDashboard, end: true }];

export interface Shortcut {
  to: string;
  label: string;
  description: string;
  icon: ComponentType<{ size?: number; 'aria-hidden'?: boolean }>;
}

export const ADMIN_SHORTCUTS: Shortcut[] = [
  { to: '/admin/users', label: 'Utenti', description: 'Crea account, disattiva, reset password.', icon: Users },
];

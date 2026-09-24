import { LayoutDashboard } from 'lucide-react';
import type { ComponentType } from 'react';
import type { NavItem } from '../layouts/AppLayout';

export const ADMIN_NAV: NavItem[] = [{ to: '/admin', label: 'Dashboard', icon: LayoutDashboard, end: true }];

export const USER_NAV: NavItem[] = [{ to: '/app', label: 'Home', icon: LayoutDashboard, end: true }];

export interface Shortcut {
  to: string;
  label: string;
  description: string;
  icon: ComponentType<{ size?: number; 'aria-hidden'?: boolean }>;
}

export const ADMIN_SHORTCUTS: Shortcut[] = [];

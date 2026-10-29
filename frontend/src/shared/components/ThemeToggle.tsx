import { Moon, Sun } from 'lucide-react';
import { useSyncExternalStore } from 'react';
import { useTheme } from '../../app/providers/theme';

function subscribeToSystemTheme(onChange: () => void) {
  const media = window.matchMedia?.('(prefers-color-scheme: dark)');
  media?.addEventListener('change', onChange);
  return () => media?.removeEventListener('change', onChange);
}

const isSystemDark = () => window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;

export function ThemeToggle() {
  const { theme, setTheme } = useTheme();
  const systemDark = useSyncExternalStore(subscribeToSystemTheme, isSystemDark);
  const dark = theme === 'dark' || (theme === 'system' && systemDark);
  return (
    <button type="button" className="theme-toggle" aria-label={dark ? 'Attiva tema chiaro' : 'Attiva tema scuro'}
      onClick={() => setTheme(dark ? 'light' : 'dark')}>
      {dark ? <Sun size={20} aria-hidden="true" /> : <Moon size={20} aria-hidden="true" />}
    </button>
  );
}

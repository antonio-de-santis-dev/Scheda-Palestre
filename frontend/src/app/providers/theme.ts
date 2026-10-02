import { createContext, useContext } from 'react';

export type ThemePreference = 'system' | 'light' | 'dark';
export const THEME_KEY = 'gymplanner-theme';
export const ThemeContext = createContext<{
  theme: ThemePreference;
  setTheme: (theme: ThemePreference) => void;
}>({ theme: 'system', setTheme: () => undefined });

export function useTheme() {
  return useContext(ThemeContext);
}

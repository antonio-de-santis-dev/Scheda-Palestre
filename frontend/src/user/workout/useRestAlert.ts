import { useEffect, useRef, useState } from 'react';

const KEY = 'gymplanner.rest-alert';
const SOUNDS = ['alert-1', 'alert-2', 'alert-3'] as const;
type Sound = (typeof SOUNDS)[number];
const url = (sound: Sound) => `${import.meta.env.BASE_URL}sounds/${sound}.mp3`;

function preference(): { enabled: boolean; sound: Sound } {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) ?? '{}') as { enabled?: boolean; sound?: string };
    return { enabled: saved.enabled === true, sound: SOUNDS.find((s) => s === saved.sound) ?? 'alert-1' };
  } catch {
    return { enabled: false, sound: 'alert-1' };
  }
}

/** The same player previews the selected clip on a user gesture and alerts at zero. */
export function useRestAlert() {
  const [settings, setSettings] = useState(preference);
  const audio = useRef<HTMLAudioElement | null>(null);
  useEffect(() => {
    const player = new Audio(url(settings.sound));
    // Download the clip only when the alert is enabled: most workouts never use it.
    player.preload = settings.enabled ? 'auto' : 'none';
    audio.current = player;
    return () => { player.pause(); audio.current = null; };
    // One player per mounted workout; settings changes update its source in the event handler.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  useEffect(() => {
    try { localStorage.setItem(KEY, JSON.stringify(settings)); } catch { /* private browsing */ }
  }, [settings]);
  const play = () => {
    if (!settings.enabled || !audio.current) return;
    audio.current.currentTime = 0;
    void audio.current.play().catch(() => { /* Browser requires another user gesture. */ });
  };
  const setEnabled = (enabled: boolean) => {
    setSettings((old) => ({ ...old, enabled }));
    if (enabled) {
      if (audio.current) {
        audio.current.preload = 'auto';
        void audio.current.play().catch(() => {});
      }
    } else audio.current?.pause();
  };
  const setSound = (sound: Sound) => {
    setSettings((old) => ({ ...old, sound }));
    if (!audio.current) return;
    audio.current.pause();
    audio.current.src = url(sound);
    audio.current.load();
    if (settings.enabled) void audio.current.play().catch(() => {});
  };
  return { ...settings, setEnabled, setSound, play };
}

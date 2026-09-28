import { useEffect, useRef, useState } from 'react';

const STORAGE_KEY = 'gymplanner.audio';

function readPreference(): boolean {
  try {
    return window.localStorage.getItem(STORAGE_KEY) === 'on';
  } catch {
    return false;
  }
}

function savePreference(on: boolean) {
  try {
    window.localStorage.setItem(STORAGE_KEY, on ? 'on' : 'off');
  } catch {
    // Storage may be blocked (private mode): the preference simply is not remembered.
  }
}

export const speechSupported = () =>
  typeof window !== 'undefined' && 'speechSynthesis' in window && typeof window.SpeechSynthesisUtterance === 'function';

/**
 * Optional Italian voice feedback (Web Speech API). Off by default, preference kept locally.
 * Voices load asynchronously (voiceschanged): without an Italian voice the utterance still uses
 * lang it-IT and the browser default. cancel() before speak() avoids overlapping messages.
 */
export function useSpeech() {
  const supported = speechSupported();
  const [enabled, setEnabled] = useState(() => supported && readPreference());
  const voice = useRef<SpeechSynthesisVoice | null>(null);

  useEffect(() => {
    if (!supported) {
      return undefined;
    }
    const synth = window.speechSynthesis;
    const pick = () => {
      voice.current = synth.getVoices().find((v) => v.lang?.toLowerCase().startsWith('it')) ?? null;
    };
    pick();
    synth.addEventListener?.('voiceschanged', pick);
    return () => synth.removeEventListener?.('voiceschanged', pick);
  }, [supported]);

  const speak = (text: string) => {
    if (!supported || !enabled) {
      return;
    }
    const utterance = new window.SpeechSynthesisUtterance(text);
    utterance.lang = 'it-IT';
    if (voice.current) {
      utterance.voice = voice.current;
    }
    window.speechSynthesis.cancel();
    window.speechSynthesis.speak(utterance);
  };

  const toggle = () => {
    const next = !enabled;
    setEnabled(next);
    savePreference(next);
    if (next && supported) {
      // The activation gesture itself unlocks audio on browsers that require it.
      const utterance = new window.SpeechSynthesisUtterance('Audio attivato');
      utterance.lang = 'it-IT';
      window.speechSynthesis.cancel();
      window.speechSynthesis.speak(utterance);
    } else if (supported) {
      window.speechSynthesis.cancel();
    }
  };

  return { supported, enabled, toggle, speak };
}

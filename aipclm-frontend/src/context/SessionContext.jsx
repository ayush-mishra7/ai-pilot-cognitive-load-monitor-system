import { createContext, useContext, useState, useCallback } from 'react';

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const SessionContext = createContext(null);

export function SessionProvider({ children }) {
  const [activeSessionId, setActiveSessionId] = useState(() => {
    try {
      const stored = localStorage.getItem('activeSessionId');
      // Only honour values that look like a real UUID — discard stale 'demo' etc.
      if (stored && UUID_RE.test(stored)) return stored;
      localStorage.removeItem('activeSessionId');
      return null;
    } catch { return null; }
  });

  const setSession = useCallback((id) => {
    const valid = id && UUID_RE.test(id) ? id : null;
    setActiveSessionId(valid);
    try {
      if (valid) localStorage.setItem('activeSessionId', valid);
      else localStorage.removeItem('activeSessionId');
    } catch { /* silent */ }
  }, []);

  const clearSession = useCallback(() => {
    setActiveSessionId(null);
    try { localStorage.removeItem('activeSessionId'); } catch { /* silent */ }
  }, []);

  return (
    <SessionContext.Provider value={{ activeSessionId, setSession, clearSession }}>
      {children}
    </SessionContext.Provider>
  );
}

export function useSession() {
  const ctx = useContext(SessionContext);
  if (!ctx) throw new Error('useSession must be used within SessionProvider');
  return ctx;
}

import { createContext, useContext, useState, useCallback, useMemo } from 'react';

const AuthContext = createContext(null);

/**
 * Provides JWT-based authentication state.
 * Stores token + user info in localStorage and keeps them in React state.
 */
export function AuthProvider({ children }) {
  const [auth, setAuth] = useState(() => {
    try {
      const stored = localStorage.getItem('aipclm_auth');
      if (stored) {
        const parsed = JSON.parse(stored);
        if (parsed?.token) return parsed;
      }
    } catch { /* corrupt data — reset */ }
    return null;
  });

  /** Called after successful login / register */
  const login = useCallback((data) => {
    // data = { token, userId, email, fullName, role, callSign, pilotId }
    const payload = {
      token: data.token,
      userId: data.userId,
      email: data.email,
      fullName: data.fullName,
      role: data.role,
      callSign: data.callSign,
      pilotId: data.pilotId || null,
    };
    setAuth(payload);
    try { localStorage.setItem('aipclm_auth', JSON.stringify(payload)); } catch { /* silent */ }
  }, []);

  /** Clear everything */
  const logout = useCallback(() => {
    setAuth(null);
    try {
      localStorage.removeItem('aipclm_auth');
      localStorage.removeItem('activeSessionId');
    } catch { /* silent */ }
  }, []);

  const value = useMemo(() => ({
    /** The full auth payload, or null if not logged in */
    auth,
    /** Whether the user is authenticated */
    isAuthenticated: !!auth?.token,
    /** Shortcut: current user's role (PILOT | ATC | ADMIN | null) */
    role: auth?.role || null,
    /** Current JWT token */
    token: auth?.token || null,
    login,
    logout,
  }), [auth, login, logout]);

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}

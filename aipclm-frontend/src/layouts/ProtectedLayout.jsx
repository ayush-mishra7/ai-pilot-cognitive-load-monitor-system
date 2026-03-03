import { Outlet, NavLink, useNavigate, Navigate } from 'react-router-dom';
import { useSession } from '../context/SessionContext';
import { useAuth } from '../context/AuthContext';

export default function ProtectedLayout({ requiredRole }) {
  const navigate = useNavigate();
  const { activeSessionId } = useSession();
  const { isAuthenticated, role, auth, logout } = useAuth();

  // Not authenticated → redirect to login
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  // Wrong role → redirect to appropriate dashboard
  if (requiredRole && role !== requiredRole && role !== 'ADMIN') {
    if (role === 'ATC') return <Navigate to="/atc" replace />;
    return <Navigate to="/home" replace />;
  }

  const linkClass = ({ isActive }) =>
    `nav-link ${isActive ? 'nav-link--active' : ''}`;

  /* Dashboard / Analytics links point to the active session, or fall back to /home */
  const dashPath = activeSessionId ? `/dashboard/${activeSessionId}` : '/home';
  const analPath = activeSessionId ? `/analytics/${activeSessionId}` : '/home';

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div className="min-h-screen" style={{ background: '#0a0c10' }}>

      {/* ─── Premium Top Nav ─── */}
      <nav className="nav-bar">
        <div className="max-w-[1600px] mx-auto px-5 py-2.5 flex items-center justify-between">

          {/* Logo / Brand */}
          <div
            className="flex items-center gap-3 cursor-pointer group"
            onClick={() => navigate('/home')}
          >
            <div className="nav-logo">
              <span className="nav-logo-text">AI</span>
            </div>
            <span className="nav-brand">PCLM</span>
            <span className="nav-brand-sub">Pilot Cognitive Load Monitor</span>
          </div>

          {/* Navigation Links */}
          <div className="flex items-center gap-1">
            <NavLink to="/home" className={linkClass}>HOME</NavLink>
            <NavLink
              to={dashPath}
              className={linkClass}
              title={activeSessionId ? `Session ${activeSessionId.slice(0, 8)}…` : 'Select a session from Home'}
            >
              DASHBOARD
            </NavLink>
            <NavLink
              to={analPath}
              className={linkClass}
              title={activeSessionId ? `Session ${activeSessionId.slice(0, 8)}…` : 'Select a session from Home'}
            >
              ANALYTICS
            </NavLink>
          </div>

          {/* User Badge + Logout */}
          <div className="flex items-center gap-3">
            <span style={{
              color: '#00FF41',
              fontFamily: 'Share Tech Mono, monospace',
              fontSize: '0.75rem',
              opacity: 0.8,
            }}>
              {auth?.callSign || auth?.fullName || 'PILOT'}
            </span>
            <button className="nav-logout" onClick={handleLogout}>
              LOGOUT
            </button>
          </div>
        </div>
      </nav>

      {/* ─── Page Content ─── */}
      <main className="pt-14 min-h-screen">
        <Outlet />
      </main>
    </div>
  );
}

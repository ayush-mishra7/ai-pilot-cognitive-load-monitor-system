import { Outlet, NavLink, useNavigate, Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function AtcLayout() {
  const navigate = useNavigate();
  const { isAuthenticated, role, auth, logout } = useAuth();

  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (role !== 'ATC' && role !== 'ADMIN') return <Navigate to="/home" replace />;

  const linkClass = ({ isActive }) =>
    `nav-link ${isActive ? 'nav-link--active' : ''}`;

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div className="min-h-screen" style={{ background: '#0a0c10' }}>

      {/* ─── ATC Top Nav — amber theme ─── */}
      <nav className="nav-bar" style={{ borderBottom: '1px solid rgba(255,184,0,0.25)' }}>
        <div className="max-w-[1600px] mx-auto px-5 py-2.5 flex items-center justify-between">

          {/* Logo / Brand */}
          <div
            className="flex items-center gap-3 cursor-pointer group"
            onClick={() => navigate('/atc')}
          >
            <div className="nav-logo" style={{ background: 'linear-gradient(135deg, rgba(255,184,0,0.25), rgba(255,184,0,0.08))' }}>
              <span className="nav-logo-text" style={{ color: '#FFB800' }}>AT</span>
            </div>
            <span className="nav-brand" style={{ color: '#FFB800' }}>ATC</span>
            <span className="nav-brand-sub">Command Center</span>
          </div>

          {/* Navigation Links */}
          <div className="flex items-center gap-1">
            <NavLink to="/atc" end className={linkClass}>RADAR</NavLink>
          </div>

          {/* User Badge + Logout */}
          <div className="flex items-center gap-3">
            <span style={{
              color: '#FFB800',
              fontFamily: 'Share Tech Mono, monospace',
              fontSize: '0.75rem',
              opacity: 0.8,
            }}>
              {auth?.callSign || auth?.fullName || 'ATC'}
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

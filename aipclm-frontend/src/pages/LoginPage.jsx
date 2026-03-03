import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { loginUser } from '../services/api';
import landingBg from '../assets/landing-page.png';

export default function LoginPage() {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleLogin = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const data = await loginUser({ email, password });
      login(data);
      // Redirect based on role
      if (data.role === 'ATC') {
        navigate('/atc');
      } else {
        navigate('/home');
      }
    } catch (err) {
      const msg = err?.response?.data?.error || err?.message || 'Login failed';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      {/* Background */}
      <div
        className="landing-bg"
        style={{ backgroundImage: `url(${landingBg})` }}
      />
      <div className="landing-fade" />

      {/* Login Card */}
      <div className="login-card">
        {/* Logo */}
        <div className="login-logo">
          <span className="login-logo__text">AI</span>
        </div>

        <h2 className="login-title">Welcome Back</h2>
        <p className="login-subtitle">
          Sign in to AI-PCLM Control Center
        </p>

        {error && (
          <div style={{
            background: 'rgba(255,50,50,0.15)',
            border: '1px solid rgba(255,50,50,0.4)',
            borderRadius: '6px',
            padding: '0.5rem 0.75rem',
            marginBottom: '0.75rem',
            fontSize: '0.8rem',
            color: '#ff6b6b',
            fontFamily: 'Rajdhani, sans-serif',
          }}>
            {error}
          </div>
        )}

        <form onSubmit={handleLogin} className="login-form">
          <div className="login-field">
            <label className="login-label">Email</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="pilot@aipclm.com"
              className="login-input"
              required
            />
          </div>

          <div className="login-field">
            <label className="login-label">Password</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              className="login-input"
              required
            />
          </div>

          <button type="submit" disabled={loading} className="login-submit">
            {loading ? (
              <span style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
                <span className="login-spinner" />
                Authenticating…
              </span>
            ) : (
              'AUTHENTICATE'
            )}
          </button>
        </form>

        <p className="login-hint">
          Don't have an account?{' '}
          <Link to="/register" style={{ color: '#00C2FF', textDecoration: 'underline' }}>
            Register
          </Link>
        </p>
        <p className="login-hint" style={{ marginTop: '0.25rem', fontSize: '0.7rem', opacity: 0.6 }}>
          Default: pilot@aipclm.com / pilot123 &nbsp;|&nbsp; tower@aipclm.com / tower123
        </p>
      </div>
    </div>
  );
}

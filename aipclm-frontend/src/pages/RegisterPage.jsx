import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { registerUser } from '../services/api';
import landingBg from '../assets/landing-page.png';

const PROFILE_TYPES = ['EXPERIENCED', 'NOVICE', 'FATIGUE_PRONE', 'HIGH_STRESS'];

export default function RegisterPage() {
  const navigate = useNavigate();
  const { login } = useAuth();

  const [form, setForm] = useState({
    email: '',
    password: '',
    fullName: '',
    role: 'PILOT',
    callSign: '',
    profileType: 'EXPERIENCED',
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const payload = {
        email: form.email,
        password: form.password,
        fullName: form.fullName,
        role: form.role,
        callSign: form.callSign || null,
        profileType: form.role === 'PILOT' ? form.profileType : null,
      };
      const data = await registerUser(payload);
      login(data);
      if (data.role === 'ATC') {
        navigate('/atc');
      } else {
        navigate('/home');
      }
    } catch (err) {
      const msg = err?.response?.data?.error || err?.message || 'Registration failed';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      {/* Background */}
      <div className="landing-bg" style={{ backgroundImage: `url(${landingBg})` }} />
      <div className="landing-fade" />

      {/* Register Card */}
      <div className="login-card" style={{ maxWidth: 440 }}>
        <div className="login-logo">
          <span className="login-logo__text">AI</span>
        </div>

        <h2 className="login-title">Create Account</h2>
        <p className="login-subtitle">Register for AI-PCLM Control Center</p>

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

        <form onSubmit={handleSubmit} className="login-form">
          {/* Role tabs */}
          <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '0.75rem' }}>
            {['PILOT', 'ATC'].map((r) => (
              <button
                key={r}
                type="button"
                onClick={() => setForm((f) => ({ ...f, role: r }))}
                className={`hud-btn ${form.role === r ? 'hud-btn--primary' : 'hud-btn--ghost'}`}
                style={{ flex: 1, padding: '0.5rem', fontSize: '0.85rem', letterSpacing: '0.05em' }}
              >
                {r === 'PILOT' ? '✈ PILOT' : '📡 ATC'}
              </button>
            ))}
          </div>

          <div className="login-field">
            <label className="login-label">Full Name</label>
            <input
              type="text"
              value={form.fullName}
              onChange={set('fullName')}
              placeholder="Cpt. John Doe"
              className="login-input"
              required
            />
          </div>

          <div className="login-field">
            <label className="login-label">Email</label>
            <input
              type="email"
              value={form.email}
              onChange={set('email')}
              placeholder="user@aipclm.com"
              className="login-input"
              required
            />
          </div>

          <div className="login-field">
            <label className="login-label">Password</label>
            <input
              type="password"
              value={form.password}
              onChange={set('password')}
              placeholder="Min 6 characters"
              className="login-input"
              required
              minLength={6}
            />
          </div>

          <div className="login-field">
            <label className="login-label">Call Sign</label>
            <input
              type="text"
              value={form.callSign}
              onChange={set('callSign')}
              placeholder={form.role === 'PILOT' ? 'ALPHA-7' : 'TOWER-1'}
              className="login-input"
              maxLength={20}
            />
          </div>

          {/* Pilot-specific: profile type */}
          {form.role === 'PILOT' && (
            <div className="login-field">
              <label className="login-label">Pilot Profile</label>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.35rem' }}>
                {PROFILE_TYPES.map((pt) => (
                  <button
                    key={pt}
                    type="button"
                    onClick={() => setForm((f) => ({ ...f, profileType: pt }))}
                    className={`hud-btn ${form.profileType === pt ? 'hud-btn--primary' : 'hud-btn--ghost'}`}
                    style={{ fontSize: '0.7rem', padding: '0.2rem 0.5rem' }}
                  >
                    {pt.replace('_', ' ')}
                  </button>
                ))}
              </div>
            </div>
          )}

          <button type="submit" disabled={loading} className="login-submit">
            {loading ? (
              <span style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
                <span className="login-spinner" />
                Creating Account…
              </span>
            ) : (
              'REGISTER'
            )}
          </button>
        </form>

        <p className="login-hint">
          Already have an account?{' '}
          <Link to="/login" style={{ color: '#00C2FF', textDecoration: 'underline' }}>
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
}

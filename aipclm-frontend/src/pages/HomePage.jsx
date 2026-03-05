import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { startSession, startCrewSession, startSensorSession, startWeatherSession, startSchedule, stopSession, listSessions, deleteSession, healthCheck, purgeAllSessions, createScenario, quickRegisterSensors, listSensorDevices, connectSensorDevice } from '../services/api';
import { useSession } from '../context/SessionContext';
import { useWebSocket } from '../hooks/useWebSocket';
import ScenarioConfigurator, { DEFAULT_SCENARIO } from '../components/ScenarioConfigurator';
import homeBg from '../assets/home-page.png';

const PROFILE_TYPES = ['NOVICE', 'EXPERIENCED', 'FATIGUE_PRONE', 'HIGH_STRESS'];

export default function HomePage() {
  const navigate = useNavigate();
  const { activeSessionId, setSession } = useSession();

  const [sessions, setSessions] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [profileType, setProfileType] = useState('NOVICE');
  const [scenarioData, setScenarioData] = useState({ ...DEFAULT_SCENARIO });
  const [backendUp, setBackendUp] = useState(null);          // null = checking
  const [filter, setFilter] = useState('ALL');                // ALL | RUNNING | COMPLETED
  const [crewMode, setCrewMode] = useState(false);            // CRM crew toggle
  const [sensorMode, setSensorMode] = useState(false);        // Wearable sensor toggle
  const [foProfileType, setFoProfileType] = useState('NOVICE'); // FO profile
  const [icaoAirport, setIcaoAirport] = useState('');           // Weather ICAO airport (e.g. KJFK)
  const [adsbMode, setAdsbMode] = useState(false);              // ADS-B traffic toggle
  const pollRef = useRef(null);

  /* ─── Fetch sessions (polled while RUNNING sessions exist) ─── */
  const fetchSessions = useCallback(async () => {
    try {
      const data = await listSessions();
      const list = Array.isArray(data) ? data : [];
      setSessions(list);
      return list;
    } catch {
      setSessions([]);
      return [];
    }
  }, []);

  /* ─── Health check ─── */
  const checkHealth = useCallback(async () => {
    try {
      await healthCheck();
      setBackendUp(true);
    } catch {
      setBackendUp(false);
    }
  }, []);

  /* ─── Initial load ─── */
  useEffect(() => {
    fetchSessions();
    checkHealth();
  }, [fetchSessions, checkHealth]);

  /* ─── WebSocket subscription — replaces setInterval polling ─── */
  useWebSocket('/topic/sessions', useCallback((data) => {
    const list = Array.isArray(data) ? data : [];
    setSessions(list);
  }, []));

  /* ─── Create & start ─── */
  const handleStart = async () => {
    setLoading(true);
    setError(null);
    try {
      let sessionId;
      if (crewMode) {
        const result = await startCrewSession(profileType, foProfileType);
        sessionId = result.sessionId;
      } else if (sensorMode) {
        const result = await startSensorSession(profileType);
        sessionId = result.sessionId;
        // Auto-register and connect all 6 sensors
        try {
          const devices = await quickRegisterSensors();
          if (Array.isArray(devices)) {
            for (const d of devices) {
              try { await connectSensorDevice(d.id, sessionId); } catch { /* ok */ }
            }
          }
        } catch { /* sensor setup optional */ }
      } else if (icaoAirport.trim().length >= 3) {
        const result = await startWeatherSession(profileType, icaoAirport.trim().toUpperCase(), adsbMode);
        sessionId = result.sessionId;
      } else {
        sessionId = await startSession(profileType);
      }
      await createScenario(sessionId, scenarioData);
      await startSchedule(sessionId);
      setSession(sessionId);                       // set as active
      await fetchSessions();
    } catch {
      setError('Could not start session. Is the backend running?');
    } finally {
      setLoading(false);
    }
  };

  /* ─── Stop ─── */
  const handleStop = async (id) => {
    try {
      await stopSession(id);
    } catch (e) {
      console.error('Stop failed:', e);
      // Even if scheduler stop failed, try to refresh to see updated status
    }
    if (activeSessionId === id) setSession(null);
    await fetchSessions();
  };

  /* ─── Delete ─── */
  const handleDelete = async (id) => {
    try {
      // Stop first if still running (ignore errors — scheduler may already be gone)
      const sess = sessions.find((s) => s.id === id);
      if (sess?.status === 'RUNNING') {
        try { await stopSession(id); } catch { /* ok */ }
      }
      await deleteSession(id);
      if (activeSessionId === id) setSession(null);
    } catch (e) {
      console.error('Delete failed:', e);
      setError('Could not delete session — ' + (e?.response?.data || e.message || 'unknown error'));
    }
    await fetchSessions();
  };

  /* ─── Delete all completed ─── */
  const handleClearCompleted = async () => {
    const completed = sessions.filter((s) => s.status === 'COMPLETED');
    for (const s of completed) {
      try { await deleteSession(s.id); } catch { /* skip */ }
    }
    await fetchSessions();
  };

  /* ─── Purge every session (nuclear reset) ─── */
  const handlePurgeAll = async () => {
    if (!window.confirm('Delete ALL sessions and their data? This cannot be undone.')) return;
    try {
      await purgeAllSessions();
      setSession(null);
    } catch (e) {
      console.error('Purge failed:', e);
      setError('Purge failed — ' + (e?.message || 'unknown'));
    }
    await fetchSessions();
  };

  /* ─── Select session ─── */
  const selectSession = (id) => {
    setSession(id);
    navigate(`/dashboard/${id}`);
  };

  /* ─── Derived ─── */
  const activeCt = sessions.filter((s) => s.status === 'RUNNING').length;
  const completedCt = sessions.filter((s) => s.status === 'COMPLETED').length;

  const filtered = filter === 'ALL' ? sessions
    : sessions.filter((s) => s.status === filter);

  const statusDot = (status) => {
    if (status === 'RUNNING')   return 'hud-dot hud-dot--green';
    if (status === 'COMPLETED') return 'hud-dot hud-dot--dim';
    if (status === 'PAUSED')    return 'hud-dot hud-dot--amber';
    return 'hud-dot hud-dot--dim';
  };

  return (
    <div className="home-wrapper">
      <div className="home-bg" style={{ backgroundImage: `url(${homeBg})` }} />
      <div className="home-fade" />

      <div className="home-content">

        {/* Header */}
        <div className="home-header">
          <h1 className="home-title">CONTROL CENTER</h1>
          <p className="home-subtitle">Mission management &amp; cognitive load monitoring</p>
        </div>

        {/* Quick action cards */}
        <div className="home-cards">

          {/* New Session */}
          <div className="hud-card">
            <div className="hud-card__label">NEW SESSION</div>
            <p className="hud-card__desc">
              Select pilot profile and launch a new simulation.
            </p>

            {/* Crew mode & Sensor mode toggles */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem', flexWrap: 'wrap' }}>
              <button
                onClick={() => { setCrewMode((v) => !v); setSensorMode(false); }}
                className={`hud-btn ${crewMode ? 'hud-btn--primary' : 'hud-btn--ghost'}`}
                style={{ fontSize: '0.7rem', padding: '0.25rem 0.6rem' }}
              >
                {crewMode ? '✈ CREW MODE' : '✈ SINGLE PILOT'}
              </button>
              <button
                onClick={() => { setSensorMode((v) => !v); setCrewMode(false); }}
                className={`hud-btn ${sensorMode ? 'hud-btn--primary' : 'hud-btn--ghost'}`}
                style={{ fontSize: '0.7rem', padding: '0.25rem 0.6rem' }}
              >
                {sensorMode ? '🩺 SENSOR MODE' : '🩺 NO SENSORS'}
              </button>
              {crewMode && (
                <span style={{ fontSize: '0.6rem', color: '#00BFFF', letterSpacing: '0.05em' }}>
                  Captain + First Officer · CRM enabled
                </span>
              )}
              {sensorMode && (
                <span style={{ fontSize: '0.6rem', color: '#E879F9', letterSpacing: '0.05em' }}>
                  All 6 wearable sensors auto-connected
                </span>
              )}
            </div>

            {/* Weather / ADS-B config (hidden in crew & sensor modes) */}
            {!crewMode && !sensorMode && (
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem', flexWrap: 'wrap' }}>
                <input
                  type="text"
                  value={icaoAirport}
                  onChange={(e) => setIcaoAirport(e.target.value.toUpperCase().slice(0, 4))}
                  placeholder="ICAO (e.g. KJFK)"
                  maxLength={4}
                  style={{
                    width: '7rem', fontSize: '0.7rem', padding: '0.25rem 0.5rem',
                    background: 'rgba(0,255,65,0.05)', border: '1px solid rgba(0,255,65,0.2)',
                    borderRadius: '4px', color: '#CCD6F6', fontFamily: "'Share Tech Mono', monospace",
                    letterSpacing: '0.1em', textTransform: 'uppercase',
                  }}
                />
                <button
                  onClick={() => setAdsbMode((v) => !v)}
                  className={`hud-btn ${adsbMode ? 'hud-btn--primary' : 'hud-btn--ghost'}`}
                  style={{ fontSize: '0.7rem', padding: '0.25rem 0.6rem' }}
                  disabled={!icaoAirport.trim()}
                >
                  {adsbMode ? '📡 ADS-B ON' : '📡 ADS-B OFF'}
                </button>
                {icaoAirport.trim() && (
                  <span style={{ fontSize: '0.6rem', color: '#00FF41', letterSpacing: '0.05em' }}>
                    Weather: {icaoAirport.trim()}{adsbMode ? ' · ADS-B traffic active' : ''}
                  </span>
                )}
              </div>
            )}

            {/* Captain / Pilot profile selector */}
            <div style={{ marginBottom: '0.15rem' }}>
              <span style={{ fontSize: '0.6rem', color: '#8892B0', letterSpacing: '0.06em' }}>
                {crewMode ? 'CAPTAIN PROFILE' : 'PILOT PROFILE'}
              </span>
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.4rem', marginBottom: crewMode ? '0.4rem' : '0.6rem' }}>
              {PROFILE_TYPES.map((pt) => (
                <button
                  key={pt}
                  onClick={() => setProfileType(pt)}
                  className={`hud-btn ${profileType === pt ? 'hud-btn--primary' : 'hud-btn--ghost'}`}
                  style={{ fontSize: '0.7rem', padding: '0.25rem 0.5rem' }}
                >
                  {pt.replace('_', ' ')}
                </button>
              ))}
            </div>

            {/* FO profile selector (crew mode only) */}
            {crewMode && (
              <>
                <div style={{ marginBottom: '0.15rem' }}>
                  <span style={{ fontSize: '0.6rem', color: '#8892B0', letterSpacing: '0.06em' }}>
                    FIRST OFFICER PROFILE
                  </span>
                </div>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.4rem', marginBottom: '0.6rem' }}>
                  {PROFILE_TYPES.map((pt) => (
                    <button
                      key={`fo-${pt}`}
                      onClick={() => setFoProfileType(pt)}
                      className={`hud-btn ${foProfileType === pt ? 'hud-btn--primary' : 'hud-btn--ghost'}`}
                      style={{ fontSize: '0.7rem', padding: '0.25rem 0.5rem' }}
                    >
                      {pt.replace('_', ' ')}
                    </button>
                  ))}
                </div>
              </>
            )}

            {/* Scenario Configurator (accordion) */}
            <ScenarioConfigurator value={scenarioData} onChange={setScenarioData} />

            <button
              onClick={handleStart}
              disabled={loading}
              className="hud-btn hud-btn--primary"
              style={{ marginTop: '0.6rem' }}
            >
              {loading ? 'INITIALIZING…' : 'CREATE & START SESSION'}
            </button>
          </div>

          {/* Quick Stats */}
          <div className="hud-card">
            <div className="hud-card__label">QUICK STATS</div>
            <div className="hud-card__stats">
              <div className="hud-stat">
                <span className="hud-stat__num hud-stat__num--green">{activeCt}</span>
                <span className="hud-stat__tag">Active</span>
              </div>
              <div className="hud-stat">
                <span className="hud-stat__num hud-stat__num--cyan">{completedCt}</span>
                <span className="hud-stat__tag">Completed</span>
              </div>
              <div className="hud-stat">
                <span className="hud-stat__num hud-stat__num--cyan">{sessions.length}</span>
                <span className="hud-stat__tag">Total</span>
              </div>
            </div>
          </div>

          {/* System Status */}
          <div className="hud-card">
            <div className="hud-card__label">SYSTEM STATUS</div>
            <div className="hud-card__status-row">
              <div className="hud-card__status-item">
                <div className={`hud-dot ${backendUp === true ? 'hud-dot--green' : backendUp === false ? 'hud-dot--red' : 'hud-dot--amber'}`} />
                <span>Backend {backendUp === true ? '(Online)' : backendUp === false ? '(Offline)' : '(Checking…)'}</span>
              </div>
            </div>
            {activeSessionId && (
              <p className="hud-card__hint" style={{ color: '#00FF41' }}>
                Active: {activeSessionId.slice(0, 12)}…
              </p>
            )}
            {!activeSessionId && (
              <p className="hud-card__hint">No session selected — create or pick one below</p>
            )}
          </div>
        </div>

        {/* Error Banner */}
        {error && (
          <div className="hud-alert">
            <span className="hud-alert__icon">⚠</span> {error}
            <button
              onClick={() => setError(null)}
              style={{ marginLeft: '1rem', color: '#FFD700', background: 'none', border: 'none', cursor: 'pointer', fontFamily: 'inherit' }}
            >
              DISMISS
            </button>
          </div>
        )}

        {/* Sessions List */}
        <div className="hud-panel">
          <div className="hud-panel__header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
            <span>FLIGHT SESSIONS</span>
            <div style={{ display: 'flex', gap: '0.3rem', alignItems: 'center' }}>
              {/* Filter buttons */}
              {['ALL', 'RUNNING', 'COMPLETED'].map((f) => (
                <button
                  key={f}
                  onClick={() => setFilter(f)}
                  className={`hud-btn ${filter === f ? 'hud-btn--primary' : 'hud-btn--ghost'}`}
                  style={{ fontSize: '0.65rem', padding: '0.2rem 0.45rem' }}
                >
                  {f}
                </button>
              ))}
              {completedCt > 0 && (
                <button
                  onClick={handleClearCompleted}
                  className="hud-btn hud-btn--danger"
                  style={{ fontSize: '0.65rem', padding: '0.2rem 0.45rem', marginLeft: '0.3rem' }}
                >
                  CLEAR COMPLETED
                </button>
              )}
              {sessions.length > 0 && (
                <button
                  onClick={handlePurgeAll}
                  className="hud-btn hud-btn--danger"
                  style={{ fontSize: '0.65rem', padding: '0.2rem 0.45rem' }}
                  title="Delete ALL sessions and their data"
                >
                  PURGE ALL
                </button>
              )}
            </div>
          </div>

          {filtered.length === 0 ? (
            <div className="hud-panel__empty">
              {filter === 'ALL'
                ? 'No sessions yet — create one above to begin monitoring.'
                : `No ${filter.toLowerCase()} sessions.`}
            </div>
          ) : (
            <div className="hud-panel__list">
              {filtered.map((s) => (
                <div
                  key={s.id}
                  className={`hud-row ${activeSessionId === s.id ? 'hud-row--active' : ''}`}
                >
                  <div className="hud-row__info">
                    <div className={statusDot(s.status)} />
                    <div>
                      <div className="hud-row__title">
                        Session {(s.id || '').slice(0, 8)}…
                      </div>
                      <div className="hud-row__meta">
                        {s.status || 'UNKNOWN'} · Pilot: {s.pilotName || 'Default'}
                        {s.totalFrames > 0 && ` · ${s.totalFrames} frames`}
                        {s.crewMode && (
                          <span style={{
                            marginLeft: '0.4rem', fontSize: '0.55rem', padding: '0.1rem 0.35rem',
                            background: 'rgba(0,191,255,0.15)', border: '1px solid rgba(0,191,255,0.4)',
                            borderRadius: '3px', color: '#00BFFF', letterSpacing: '0.08em'
                          }}>CREW</span>
                        )}
                        {s.sensorMode && (
                          <span style={{
                            marginLeft: '0.4rem', fontSize: '0.55rem', padding: '0.1rem 0.35rem',
                            background: 'rgba(232,121,249,0.15)', border: '1px solid rgba(232,121,249,0.4)',
                            borderRadius: '3px', color: '#E879F9', letterSpacing: '0.08em'
                          }}>SENSOR</span>
                        )}
                        {s.icaoAirport && (
                          <span style={{
                            marginLeft: '0.4rem', fontSize: '0.55rem', padding: '0.1rem 0.35rem',
                            background: 'rgba(0,255,65,0.12)', border: '1px solid rgba(0,255,65,0.35)',
                            borderRadius: '3px', color: '#00FF41', letterSpacing: '0.08em'
                          }}>WX:{s.icaoAirport}</span>
                        )}
                        {s.adsbMode && (
                          <span style={{
                            marginLeft: '0.4rem', fontSize: '0.55rem', padding: '0.1rem 0.35rem',
                            background: 'rgba(0,194,255,0.12)', border: '1px solid rgba(0,194,255,0.35)',
                            borderRadius: '3px', color: '#00C2FF', letterSpacing: '0.08em'
                          }}>ADS-B</span>
                        )}
                      </div>
                    </div>
                  </div>

                  <div className="hud-row__actions">
                    <button
                      className="hud-btn hud-btn--ghost"
                      onClick={() => selectSession(s.id)}
                    >
                      Dashboard
                    </button>
                    <button
                      className="hud-btn hud-btn--ghost"
                      onClick={() => { setSession(s.id); navigate(`/analytics/${s.id}`); }}
                    >
                      Analytics
                    </button>
                    {s.status === 'RUNNING' && (
                      <button
                        className="hud-btn hud-btn--danger"
                        onClick={() => handleStop(s.id)}
                      >
                        Stop
                      </button>
                    )}
                    {s.status !== 'RUNNING' && (
                      <button
                        className="hud-btn hud-btn--ghost"
                        style={{ color: '#FF6B35' }}
                        onClick={() => handleDelete(s.id)}
                      >
                        Delete
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

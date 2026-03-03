import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { listSessions } from '../services/api';

export default function AtcRadarPage() {
  const navigate = useNavigate();
  const [sessions, setSessions] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchFlights = useCallback(async () => {
    try {
      const data = await listSessions();
      setSessions(Array.isArray(data) ? data : []);
    } catch {
      setSessions([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchFlights();
    const interval = setInterval(fetchFlights, 3000);
    return () => clearInterval(interval);
  }, [fetchFlights]);

  const running = sessions.filter((s) => s.status === 'RUNNING');
  const completed = sessions.filter((s) => s.status === 'COMPLETED');

  const riskColor = (risk) => {
    if (risk === 'CRITICAL') return '#FF3333';
    if (risk === 'HIGH') return '#FF8C00';
    if (risk === 'MEDIUM') return '#FFD700';
    return '#00FF41';
  };

  return (
    <div style={{ padding: '2rem', maxWidth: 1400, margin: '0 auto' }}>

      {/* Header */}
      <div style={{ marginBottom: '2rem' }}>
        <h1 style={{
          fontFamily: 'Orbitron, sans-serif',
          fontSize: '1.8rem',
          color: '#FFB800',
          letterSpacing: '0.08em',
          marginBottom: '0.25rem',
        }}>
          ATC COMMAND CENTER
        </h1>
        <p style={{
          fontFamily: 'Rajdhani, sans-serif',
          color: 'rgba(255,255,255,0.5)',
          fontSize: '0.9rem',
        }}>
          Real-time flight monitoring and risk assessment
        </p>
      </div>

      {/* Stats bar */}
      <div style={{
        display: 'flex',
        gap: '1.5rem',
        marginBottom: '2rem',
        flexWrap: 'wrap',
      }}>
        {[
          { label: 'ACTIVE FLIGHTS', value: running.length, color: '#00FF41' },
          { label: 'COMPLETED', value: completed.length, color: '#00C2FF' },
          { label: 'TOTAL', value: sessions.length, color: '#FFB800' },
        ].map((s) => (
          <div key={s.label} style={{
            background: 'rgba(255,255,255,0.03)',
            border: '1px solid rgba(255,184,0,0.15)',
            borderRadius: '8px',
            padding: '1rem 1.5rem',
            minWidth: 140,
          }}>
            <div style={{
              fontFamily: 'Orbitron, sans-serif',
              fontSize: '1.6rem',
              color: s.color,
            }}>{s.value}</div>
            <div style={{
              fontFamily: 'Share Tech Mono, monospace',
              fontSize: '0.7rem',
              color: 'rgba(255,255,255,0.4)',
              letterSpacing: '0.1em',
            }}>{s.label}</div>
          </div>
        ))}
      </div>

      {/* Radar Display Placeholder */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: '1fr 360px',
        gap: '1.5rem',
      }}>

        {/* Radar circle */}
        <div style={{
          background: 'rgba(0,0,0,0.4)',
          border: '1px solid rgba(255,184,0,0.2)',
          borderRadius: '12px',
          padding: '2rem',
          position: 'relative',
          overflow: 'hidden',
          minHeight: 400,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}>
          {/* Radar sweep */}
          <div style={{
            width: 320,
            height: 320,
            borderRadius: '50%',
            border: '1px solid rgba(255,184,0,0.3)',
            position: 'relative',
          }}>
            {/* Concentric rings */}
            {[0.33, 0.66].map((s) => (
              <div key={s} style={{
                position: 'absolute',
                inset: `${(1 - s) * 50}%`,
                borderRadius: '50%',
                border: '1px solid rgba(255,184,0,0.1)',
              }} />
            ))}
            {/* Cross lines */}
            <div style={{
              position: 'absolute', top: '50%', left: 0, right: 0,
              height: 1, background: 'rgba(255,184,0,0.1)',
            }} />
            <div style={{
              position: 'absolute', left: '50%', top: 0, bottom: 0,
              width: 1, background: 'rgba(255,184,0,0.1)',
            }} />

            {/* Sweep animation */}
            <div className="radar-sweep" style={{
              position: 'absolute',
              inset: 0,
              borderRadius: '50%',
              background: 'conic-gradient(from 0deg, transparent 0deg, rgba(255,184,0,0.12) 30deg, transparent 60deg)',
              animation: 'radar-rotate 4s linear infinite',
            }} />

            {/* Flight blips */}
            {running.map((s, i) => {
              const angle = (i * 137.5) * (Math.PI / 180); // golden angle distribution
              const r = 60 + (i * 35) % 120;
              const x = 160 + r * Math.cos(angle);
              const y = 160 + r * Math.sin(angle);
              return (
                <div
                  key={s.id}
                  onClick={() => navigate(`/atc/flight/${s.id}`)}
                  style={{
                    position: 'absolute',
                    left: x - 6,
                    top: y - 6,
                    width: 12,
                    height: 12,
                    borderRadius: '50%',
                    background: riskColor(s.riskLevel || 'LOW'),
                    boxShadow: `0 0 8px ${riskColor(s.riskLevel || 'LOW')}`,
                    cursor: 'pointer',
                    zIndex: 10,
                    animation: 'pulse-blip 2s infinite',
                  }}
                  title={`Session ${(s.id || '').slice(0, 8)} — ${s.status}`}
                />
              );
            })}

            {/* Center dot */}
            <div style={{
              position: 'absolute',
              top: '50%',
              left: '50%',
              transform: 'translate(-50%, -50%)',
              width: 6,
              height: 6,
              borderRadius: '50%',
              background: '#FFB800',
            }} />
          </div>

          {loading && (
            <div style={{
              position: 'absolute',
              color: 'rgba(255,184,0,0.5)',
              fontFamily: 'Share Tech Mono, monospace',
              fontSize: '0.8rem',
              bottom: '1rem',
            }}>SCANNING...</div>
          )}
        </div>

        {/* Flight Strip Panel */}
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: '0.75rem',
          maxHeight: 500,
          overflowY: 'auto',
        }}>
          <div style={{
            fontFamily: 'Orbitron, sans-serif',
            fontSize: '0.8rem',
            color: '#FFB800',
            letterSpacing: '0.1em',
            marginBottom: '0.25rem',
          }}>FLIGHT STRIPS</div>

          {running.length === 0 && (
            <div style={{
              color: 'rgba(255,255,255,0.3)',
              fontFamily: 'Rajdhani, sans-serif',
              fontSize: '0.85rem',
              padding: '2rem 1rem',
              textAlign: 'center',
            }}>No active flights</div>
          )}

          {running.map((s) => (
            <div
              key={s.id}
              onClick={() => navigate(`/atc/flight/${s.id}`)}
              style={{
                background: 'rgba(255,255,255,0.03)',
                border: '1px solid rgba(255,184,0,0.15)',
                borderLeft: `3px solid ${riskColor(s.riskLevel || 'LOW')}`,
                borderRadius: '6px',
                padding: '0.75rem 1rem',
                cursor: 'pointer',
                transition: 'background 0.2s',
              }}
              onMouseEnter={(e) => e.currentTarget.style.background = 'rgba(255,184,0,0.06)'}
              onMouseLeave={(e) => e.currentTarget.style.background = 'rgba(255,255,255,0.03)'}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{
                  fontFamily: 'Orbitron, sans-serif',
                  fontSize: '0.8rem',
                  color: '#fff',
                }}>
                  {(s.id || '').slice(0, 8).toUpperCase()}
                </span>
                <span style={{
                  fontFamily: 'Share Tech Mono, monospace',
                  fontSize: '0.7rem',
                  color: riskColor(s.riskLevel || 'LOW'),
                  fontWeight: 700,
                }}>
                  {s.riskLevel || 'LOW'}
                </span>
              </div>
              <div style={{
                fontFamily: 'Rajdhani, sans-serif',
                fontSize: '0.75rem',
                color: 'rgba(255,255,255,0.5)',
                marginTop: '0.25rem',
              }}>
                {s.pilotName || 'Pilot'} · {s.totalFrames || 0} frames
              </div>
            </div>
          ))}

          {/* Completed section */}
          {completed.length > 0 && (
            <>
              <div style={{
                fontFamily: 'Share Tech Mono, monospace',
                fontSize: '0.65rem',
                color: 'rgba(255,255,255,0.3)',
                letterSpacing: '0.1em',
                marginTop: '0.5rem',
              }}>COMPLETED ({completed.length})</div>
              {completed.slice(0, 5).map((s) => (
                <div
                  key={s.id}
                  onClick={() => navigate(`/atc/flight/${s.id}`)}
                  style={{
                    background: 'rgba(255,255,255,0.02)',
                    border: '1px solid rgba(255,255,255,0.08)',
                    borderLeft: '3px solid rgba(255,255,255,0.15)',
                    borderRadius: '6px',
                    padding: '0.6rem 1rem',
                    cursor: 'pointer',
                    opacity: 0.6,
                  }}
                >
                  <div style={{
                    fontFamily: 'Share Tech Mono, monospace',
                    fontSize: '0.7rem',
                    color: 'rgba(255,255,255,0.5)',
                  }}>
                    {(s.id || '').slice(0, 8)} · COMPLETED · {s.totalFrames || 0} frames
                  </div>
                </div>
              ))}
            </>
          )}
        </div>
      </div>

      {/* Radar CSS animation */}
      <style>{`
        @keyframes radar-rotate {
          from { transform: rotate(0deg); }
          to   { transform: rotate(360deg); }
        }
        @keyframes pulse-blip {
          0%, 100% { opacity: 1; transform: scale(1); }
          50% { opacity: 0.6; transform: scale(1.4); }
        }
      `}</style>
    </div>
  );
}

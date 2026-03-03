import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getLatestState, getCognitiveHistory, getRiskHistory } from '../services/api';

export default function AtcFlightDetailPage() {
  const { sessionId } = useParams();
  const navigate = useNavigate();

  const [latest, setLatest] = useState(null);
  const [cogHistory, setCogHistory] = useState([]);
  const [riskHistory, setRiskHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchAll = useCallback(async () => {
    try {
      const [lat, cog, risk] = await Promise.all([
        getLatestState(sessionId),
        getCognitiveHistory(sessionId),
        getRiskHistory(sessionId),
      ]);
      setLatest(lat);
      setCogHistory(Array.isArray(cog) ? cog : []);
      setRiskHistory(Array.isArray(risk) ? risk : []);
      setError(null);
    } catch (e) {
      setError(e?.response?.status === 404 ? 'Flight not found' : 'Failed to load flight data');
    } finally {
      setLoading(false);
    }
  }, [sessionId]);

  useEffect(() => {
    fetchAll();
    const interval = setInterval(fetchAll, 2000);
    return () => clearInterval(interval);
  }, [fetchAll]);

  const riskColor = (r) => {
    if (r === 'CRITICAL') return '#FF3333';
    if (r === 'HIGH') return '#FF8C00';
    if (r === 'MEDIUM') return '#FFD700';
    return '#00FF41';
  };

  if (loading) {
    return (
      <div style={{ padding: '3rem', textAlign: 'center', color: '#FFB800', fontFamily: 'Share Tech Mono, monospace' }}>
        LOADING FLIGHT DATA...
      </div>
    );
  }

  if (error) {
    return (
      <div style={{ padding: '3rem', textAlign: 'center' }}>
        <p style={{ color: '#FF3333', fontFamily: 'Share Tech Mono, monospace', marginBottom: '1rem' }}>{error}</p>
        <button onClick={() => navigate('/atc')} className="hud-btn hud-btn--ghost">← BACK TO RADAR</button>
      </div>
    );
  }

  const t = latest?.telemetry || {};
  const c = latest?.cognitiveState || {};
  const r = latest?.riskAssessment || {};
  const recs = latest?.recommendations || [];

  return (
    <div style={{ padding: '2rem', maxWidth: 1400, margin: '0 auto' }}>

      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '2rem' }}>
        <button
          onClick={() => navigate('/atc')}
          className="hud-btn hud-btn--ghost"
          style={{ fontSize: '0.8rem' }}
        >← RADAR</button>
        <h1 style={{
          fontFamily: 'Orbitron, sans-serif',
          fontSize: '1.4rem',
          color: '#FFB800',
          letterSpacing: '0.08em',
        }}>
          FLIGHT {(sessionId || '').slice(0, 8).toUpperCase()}
        </h1>
        <span style={{
          fontFamily: 'Share Tech Mono, monospace',
          fontSize: '0.8rem',
          color: riskColor(r.riskLevel || c.riskLevel || 'LOW'),
          background: `${riskColor(r.riskLevel || c.riskLevel || 'LOW')}15`,
          padding: '0.2rem 0.6rem',
          borderRadius: '4px',
          fontWeight: 700,
        }}>
          {r.riskLevel || c.riskLevel || 'LOW'}
        </span>
      </div>

      {/* Info cards grid */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
        gap: '1rem',
        marginBottom: '2rem',
      }}>

        {/* Telemetry */}
        <InfoCard title="TELEMETRY" color="#00C2FF">
          <DataRow label="Altitude" value={`${(t.altitude || 0).toFixed(0)} ft`} />
          <DataRow label="Airspeed" value={`${(t.airspeed || 0).toFixed(0)} kt`} />
          <DataRow label="Heading" value={`${(t.heading || 0).toFixed(0)}°`} />
          <DataRow label="V/Speed" value={`${(t.verticalSpeed || 0).toFixed(0)} fpm`} />
          <DataRow label="Phase" value={t.phaseOfFlight || '—'} />
          <DataRow label="Turbulence" value={`${((t.turbulenceLevel || 0) * 100).toFixed(0)}%`} />
          <DataRow label="Autopilot" value={t.autopilotEngaged ? 'ON' : 'OFF'} />
        </InfoCard>

        {/* Cognitive */}
        <InfoCard title="COGNITIVE STATE" color="#00FF41">
          <DataRow label="Expert Load" value={`${(c.expertComputedLoad || 0).toFixed(1)}`} />
          <DataRow label="ML Load" value={`${(c.mlPredictedLoad || 0).toFixed(1)}`} />
          <DataRow label="Smoothed" value={`${(c.smoothedLoad || 0).toFixed(1)}`} />
          <DataRow label="Error Prob" value={`${((c.errorProbability || 0) * 100).toFixed(0)}%`} />
          <DataRow label="Confidence" value={`${((c.confidenceScore || 0) * 100).toFixed(0)}%`} />
          <DataRow label="Fatigue Slope" value={`${(c.fatigueTrendSlope || 0).toFixed(3)}`} />
          <DataRow label="Swiss Cheese" value={`${((c.swissCheeseAlignmentScore || 0) * 100).toFixed(0)}%`} />
        </InfoCard>

        {/* Risk */}
        <InfoCard title="RISK ASSESSMENT" color="#FFB800">
          <DataRow label="Risk Level" value={r.riskLevel || '—'} highlight={riskColor(r.riskLevel || 'LOW')} />
          <DataRow label="Aggregated" value={`${(r.aggregatedRiskScore || 0).toFixed(1)}`} />
          <DataRow label="Delayed Rx" value={`${((r.delayedReactionProbability || 0) * 100).toFixed(0)}%`} />
          <DataRow label="Unsafe Desc" value={`${((r.unsafeDescentProbability || 0) * 100).toFixed(0)}%`} />
          <DataRow label="Missed Chk" value={`${((r.missedChecklistProbability || 0) * 100).toFixed(0)}%`} />
          <DataRow label="Escalated" value={r.riskEscalated ? 'YES ⚠' : 'NO'} highlight={r.riskEscalated ? '#FF3333' : null} />
          <DataRow label="Swiss Cheese" value={r.swissCheeseTriggered ? 'TRIGGERED ⚠' : 'CLEAR'} highlight={r.swissCheeseTriggered ? '#FF3333' : null} />
        </InfoCard>

        {/* Biometrics */}
        <InfoCard title="PILOT BIOMETRICS" color="#E040FB">
          <DataRow label="Heart Rate" value={`${(t.heartRate || 0).toFixed(0)} BPM`} />
          <DataRow label="Blink Rate" value={`${(t.blinkRate || 0).toFixed(1)} /min`} />
          <DataRow label="Fatigue" value={`${(t.fatigueIndex || 0).toFixed(1)}`} />
          <DataRow label="Stress" value={`${(t.stressIndex || 0).toFixed(1)}`} />
          <DataRow label="Reaction" value={`${(t.reactionTimeMs || 0).toFixed(0)} ms`} />
          <DataRow label="Error Count" value={`${t.errorCount || 0}`} />
        </InfoCard>
      </div>

      {/* Recommendations */}
      {recs.length > 0 && (
        <div style={{
          background: 'rgba(255,255,255,0.03)',
          border: '1px solid rgba(255,184,0,0.15)',
          borderRadius: '8px',
          padding: '1rem 1.25rem',
          marginBottom: '2rem',
        }}>
          <div style={{
            fontFamily: 'Orbitron, sans-serif',
            fontSize: '0.75rem',
            color: '#FFB800',
            letterSpacing: '0.1em',
            marginBottom: '0.75rem',
          }}>AI RECOMMENDATIONS</div>
          {recs.map((rec, i) => {
            const sevColor = rec.severity === 'CRITICAL' ? '#FF3333'
              : rec.severity === 'WARNING' ? '#FF8C00'
              : rec.severity === 'CAUTION' ? '#FFD700'
              : '#00C2FF';
            return (
              <div key={i} style={{
                display: 'flex',
                gap: '0.75rem',
                alignItems: 'flex-start',
                padding: '0.4rem 0',
                borderBottom: i < recs.length - 1 ? '1px solid rgba(255,255,255,0.05)' : 'none',
              }}>
                <span style={{
                  fontFamily: 'Share Tech Mono, monospace',
                  fontSize: '0.65rem',
                  color: sevColor,
                  background: `${sevColor}15`,
                  padding: '0.15rem 0.4rem',
                  borderRadius: '3px',
                  whiteSpace: 'nowrap',
                }}>{rec.severity}</span>
                <span style={{
                  fontFamily: 'Rajdhani, sans-serif',
                  fontSize: '0.8rem',
                  color: 'rgba(255,255,255,0.75)',
                }}>{rec.message || rec.recommendationType}</span>
              </div>
            );
          })}
        </div>
      )}

      {/* History counts */}
      <div style={{
        fontFamily: 'Share Tech Mono, monospace',
        fontSize: '0.7rem',
        color: 'rgba(255,255,255,0.3)',
        display: 'flex',
        gap: '2rem',
      }}>
        <span>Cognitive frames: {cogHistory.length}</span>
        <span>Risk frames: {riskHistory.length}</span>
      </div>
    </div>
  );
}

function InfoCard({ title, color, children }) {
  return (
    <div style={{
      background: 'rgba(255,255,255,0.03)',
      border: `1px solid ${color}22`,
      borderTop: `2px solid ${color}55`,
      borderRadius: '8px',
      padding: '1rem 1.25rem',
    }}>
      <div style={{
        fontFamily: 'Orbitron, sans-serif',
        fontSize: '0.7rem',
        color,
        letterSpacing: '0.1em',
        marginBottom: '0.5rem',
      }}>{title}</div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.2rem' }}>
        {children}
      </div>
    </div>
  );
}

function DataRow({ label, value, highlight }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.78rem' }}>
      <span style={{ fontFamily: 'Rajdhani, sans-serif', color: 'rgba(255,255,255,0.45)' }}>{label}</span>
      <span style={{
        fontFamily: 'Share Tech Mono, monospace',
        color: highlight || 'rgba(255,255,255,0.85)',
        fontWeight: highlight ? 700 : 400,
      }}>{value}</span>
    </div>
  );
}

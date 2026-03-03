import { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getLatestState } from '../services/api';
import { useSession } from '../context/SessionContext';
import dashboardBg from '../assets/dashboard-page.png';

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/*
 * Screen boundaries (% of the 1536×1024 cockpit image)
 * Derived from pixel-level brightness analysis of the three dark display areas
 */
const S = {
  L: { left: '4.6%',  top: '31.1%', width: '26.7%', height: '40.2%' },
  C: { left: '35.4%', top: '31.1%', width: '28.6%', height: '40.2%' },
  R: { left: '68.5%', top: '30.9%', width: '26.3%', height: '40.4%' },
};

const SEV_CLR = { CRITICAL: '#FF3333', WARNING: '#FFD700', CAUTION: '#FFAA00', INFO: '#00FF41' };
const SEV_ORD = { CRITICAL: 0, WARNING: 1, CAUTION: 2, INFO: 3 };

export default function DashboardPage() {
  const { sessionId } = useParams();
  const navigate = useNavigate();
  const { setSession } = useSession();
  const [state, setState] = useState(null);
  const [status, setStatus] = useState('loading');   // loading | live | waiting | not-found
  const pollRef = useRef(null);

  /* Set this session as the globally active one */
  useEffect(() => {
    if (UUID_RE.test(sessionId)) setSession(sessionId);
  }, [sessionId, setSession]);

  /* If sessionId isn't a valid UUID, skip API calls entirely */
  const validSession = UUID_RE.test(sessionId);

  const fetchState = useCallback(async () => {
    if (!validSession) { setStatus('not-found'); return; }
    try {
      const data = await getLatestState(sessionId);
      setState(data);
      setStatus('live');
    } catch (err) {
      if (err?.response?.status === 404) {
        // Session exists but no frames yet, OR session doesn't exist
        if (status === 'loading') setStatus('waiting');
        // If we've been live before and now get 404, session may have been deleted
        else if (status === 'live') setStatus('waiting');
      }
      // On other errors, keep showing stale data (values "freeze" — visible to pilot)
    }
  }, [sessionId, status]);

  useEffect(() => {
    setStatus('loading');
    setState(null);
    fetchState();
    pollRef.current = setInterval(fetchState, 1000);
    return () => clearInterval(pollRef.current);
  }, [sessionId]); // eslint-disable-line react-hooks/exhaustive-deps

  /* ── Derived values ── */
  const cog       = state?.cognitiveState ?? {};
  const tel       = state?.telemetry ?? {};
  const recs      = state?.recommendations ?? [];
  const cogLoad   = cog.smoothedLoad ?? 0;
  const expert    = cog.expertComputedLoad ?? 0;
  const ml        = cog.mlPredictedLoad ?? 0;
  const riskLevel = cog.riskLevel || 'LOW';
  const errP      = cog.errorProbability ?? 0;
  const gauge     = Math.min(1, Math.max(0, cogLoad / 10));

  const riskClr = riskLevel === 'LOW' ? '#00FF41'
    : riskLevel === 'MEDIUM' ? '#FFD700'
    : riskLevel === 'HIGH' ? '#FF6B35' : '#FF3333';

  const sorted = [...recs].sort(
    (a, b) => (SEV_ORD[a.severity] ?? 9) - (SEV_ORD[b.severity] ?? 9)
  );

  /* ── Telemetry rows ── */
  const tRows = [
    { label: 'ALT',      val: (tel.altitude ?? 0).toFixed(0),                  unit: 'FT' },
    { label: 'AIRSPD',   val: (tel.airspeed ?? 0).toFixed(0),                  unit: 'KTS' },
    { label: 'V/S',      val: '0',                                              unit: 'FPM' },
    { label: 'HR',       val: (tel.heartRate ?? 0).toFixed(0),                 unit: 'BPM',  color: '#FF6B35' },
    { label: 'FATIGUE',  val: (tel.fatigueIndex ?? 0).toFixed(2),              unit: '' },
    { label: 'STRESS',   val: (tel.stressIndex ?? 0).toFixed(2),               unit: '',      color: '#FFD700' },
    { label: 'TURB',     val: ((tel.turbulenceLevel ?? 0) * 100).toFixed(1),   unit: '%',     color: '#00C2FF' },
    { label: 'ERR PROB', val: (errP * 100).toFixed(1),                         unit: '%',     color: '#FF6B35' },
  ];

  /* ── Loading / waiting / not-found states ── */
  if (status === 'loading' || status === 'waiting' || status === 'not-found') {
    const heading = status === 'not-found' ? 'INVALID SESSION'
      : status === 'loading' ? 'CONNECTING…' : 'AWAITING FIRST FRAME';
    const sub = status === 'not-found'
      ? 'Go back to Home and select or create a valid session.'
      : status === 'waiting' ? 'Simulation initializing — data arrives in ~2s' : '';
    return (
      <div className="cockpit-wrapper">
        <div className="cockpit-frame" style={{ backgroundImage: `url(${dashboardBg})` }}>
          <div className="screen-overlay" style={{ ...S.C, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <div style={{ textAlign: 'center' }}>
              <div className="digi" style={{ fontSize: '1.6em', color: status === 'not-found' ? '#FF3333' : '#FFD700', marginBottom: '0.5em' }}>
                {heading}
              </div>
              <div className="digi-label" style={{ fontSize: '1em', lineHeight: 1.5 }}>
                {status !== 'not-found' && <>Session: {sessionId?.slice(0, 12)}…<br /></>}
                {sub}
              </div>
              <button
                onClick={() => navigate('/home')}
                className="digi"
                style={{
                  marginTop: '1.2em', padding: '0.4em 1.2em',
                  background: 'rgba(0,255,65,0.1)', border: '1px solid rgba(0,255,65,0.3)',
                  borderRadius: '6px', color: '#00FF41', fontSize: '0.9em', cursor: 'pointer',
                }}
              >
                ← BACK TO HOME
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="cockpit-wrapper">
      <div
        className="cockpit-frame"
        style={{ backgroundImage: `url(${dashboardBg})` }}
      >
        {/* ══════ LEFT SCREEN: TELEMETRY ══════ */}
        <div className="screen-overlay" style={{ ...S.L, padding: '5% 6%' }}>
          <div style={{ display: 'flex', flexDirection: 'column', justifyContent: 'space-between', height: '100%' }}>
            {tRows.map((t) => (
              <div key={t.label} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
                <span className="digi-label">{t.label}</span>
                <span>
                  <span className="digi-value" style={t.color ? { color: t.color } : undefined}>
                    {t.val}
                  </span>
                  {t.unit && (
                    <span className="digi-label" style={{ marginLeft: '0.3em', fontSize: '0.9em' }}>
                      {t.unit}
                    </span>
                  )}
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* ══════ CENTER SCREEN: COGNITIVE LOAD ══════ */}
        <div className="screen-overlay" style={{ ...S.C, padding: '3% 4%' }}>
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%' }}>
            <SemiGauge value={gauge} riskLevel={riskLevel} cogLoad={cogLoad} riskColor={riskClr} />
            <div style={{ display: 'flex', justifyContent: 'center', gap: '12%', width: '100%', marginTop: 'auto' }}>
              <MiniVal label="EXPERT" value={expert} />
              <MiniVal label="ML" value={ml} color="#00C2FF" />
              <MiniVal label="SMOOTHED" value={cogLoad} color={riskClr} />
            </div>
          </div>
        </div>

        {/* ══════ RIGHT SCREEN: RISK & RECOMMENDATIONS ══════ */}
        <div className="screen-overlay" style={{ ...S.R, padding: '5% 5%' }}>
          <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            {/* Risk Level */}
            <div style={{ textAlign: 'center', marginBottom: '4%' }}>
              <div className="digi-label" style={{ marginBottom: '0.3em' }}>RISK LEVEL</div>
              <div className="digi" style={{ fontSize: '2.8em', color: riskClr, lineHeight: 1 }}>
                {riskLevel}
              </div>
            </div>

            {/* Recommendations header */}
            <div className="digi-label" style={{ marginBottom: '0.5em', fontSize: '1.05em' }}>
              RECOMMENDATIONS ({sorted.length})
            </div>

            {/* Recommendations list */}
            <div style={{ flex: 1, overflow: 'hidden' }}>
              {sorted.length === 0 ? (
                <div className="digi" style={{ opacity: 0.4, fontSize: '1.1em', textAlign: 'center', marginTop: '2em' }}>
                  NO ACTIVE ALERTS
                </div>
              ) : (
                sorted.map((r, i) => (
                  <div key={i} style={{ display: 'flex', alignItems: 'flex-start', gap: '0.4em', marginBottom: '0.6em', lineHeight: 1.3 }}>
                    <span className="digi" style={{ color: SEV_CLR[r.severity], fontSize: '1em', flexShrink: 0, whiteSpace: 'nowrap' }}>
                      [{r.severity}]
                    </span>
                    <span className="digi" style={{ fontSize: '1em', opacity: 0.85, wordBreak: 'break-word' }}>
                      {r.message}
                    </span>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

/* ═══════════════════════════════════════════════
   Semicircular Cognitive Load Gauge (SVG)
   Green → Yellow → Red gradient arc with live fill
   ═══════════════════════════════════════════════ */
function SemiGauge({ value, riskLevel, cogLoad, riskColor }) {
  const r = 78, cx = 100, cy = 100;
  const arc = Math.PI * r;
  const off = arc * (1 - value);

  // Needle: 0→left (180°), 1→right (0°)
  const angle = Math.PI * (1 - value);
  const needleLen = r * 0.82;
  const nx = cx + needleLen * Math.cos(angle);
  const ny = cy - needleLen * Math.sin(angle);

  return (
    <svg viewBox="0 0 200 120" style={{ width: '100%', flexShrink: 0 }}>
      <defs>
        <linearGradient id="gGrad">
          <stop offset="0%"  stopColor="#00FF41" />
          <stop offset="45%" stopColor="#FFD700" />
          <stop offset="100%" stopColor="#FF0000" />
        </linearGradient>
        <filter id="needleGlow">
          <feGaussianBlur stdDeviation="2" result="blur" />
          <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
        </filter>
      </defs>
      {/* Background arc */}
      <path
        d={`M ${cx - r} ${cy} A ${r} ${r} 0 0 1 ${cx + r} ${cy}`}
        fill="none" stroke="rgba(0,255,65,0.06)" strokeWidth="12"
      />
      {/* Filled arc — animated */}
      <path
        d={`M ${cx - r} ${cy} A ${r} ${r} 0 0 1 ${cx + r} ${cy}`}
        fill="none" stroke="url(#gGrad)" strokeWidth="12" strokeLinecap="round"
        strokeDasharray={arc} strokeDashoffset={off}
        style={{ transition: 'stroke-dashoffset 0.6s ease-out' }}
      />
      {/* Needle */}
      <line
        x1={cx} y1={cy} x2={nx} y2={ny}
        stroke={riskColor} strokeWidth="2.5" strokeLinecap="round"
        filter="url(#needleGlow)"
        style={{ transition: 'x2 0.6s ease-out, y2 0.6s ease-out' }}
      />
      {/* Needle hub */}
      <circle cx={cx} cy={cy} r="5" fill={riskColor} opacity="0.9" />
      <circle cx={cx} cy={cy} r="2.5" fill="#111" />
      {/* Risk state label */}
      <text
        x={cx} y={cy - 30} textAnchor="middle"
        fill={riskColor}
        fontFamily="'Share Tech Mono', monospace"
        fontSize="28" fontWeight="bold"
        stroke="rgba(255,255,255,0.15)" strokeWidth="0.5" paintOrder="stroke"
      >
        {riskLevel}
      </text>
      {/* Numeric load */}
      <text
        x={cx} y={cy - 10} textAnchor="middle"
        fill={riskColor}
        fontFamily="'Share Tech Mono', monospace"
        fontSize="16" opacity="0.7"
      >
        {cogLoad.toFixed(1)}
      </text>
    </svg>
  );
}

/* ═══════════════════════════════════════════════
   Mini Value display (Expert / ML / Smoothed)
   ═══════════════════════════════════════════════ */
function MiniVal({ label, value, color }) {
  return (
    <div style={{ textAlign: 'center' }}>
      <div className="digi-label" style={{ fontSize: '0.95em' }}>{label}</div>
      <div className="digi-value" style={{ fontSize: '1.4em', ...(color && { color }) }}>
        {value.toFixed(1)}
      </div>
    </div>
  );
}

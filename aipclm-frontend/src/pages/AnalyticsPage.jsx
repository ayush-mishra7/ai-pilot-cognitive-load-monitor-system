import { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getCognitiveHistory, getRiskHistory, getExplainability } from '../services/api';
import { useSession } from '../context/SessionContext';
import { useWebSocket } from '../hooks/useWebSocket';
import analyticsBg from '../assets/analytics-page.png';

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const RISK_ORDER = { LOW: 1, MEDIUM: 2, HIGH: 3, CRITICAL: 4 };
const RISK_CLR  = { LOW: '#00FF41', MEDIUM: '#FFD700', HIGH: '#FF6B35', CRITICAL: '#FF3333' };

/*
 * Screen boundaries (% of the 1536×1024 cockpit image)
 * Derived from pixel-level brightness analysis of the three dark display areas.
 * Labels are baked into the image at ~Y≈285 — content padding pushes below them.
 */
const S = {
  L: { left: '4.2%',  top: '26.1%', width: '27.0%', height: '50.1%' },
  C: { left: '35.0%', top: '26.1%', width: '29.1%', height: '50.1%' },
  R: { left: '67.1%', top: '25.8%', width: '29.0%', height: '50.4%' },
};

export default function AnalyticsPage() {
  const { sessionId } = useParams();
  const navigate = useNavigate();
  const { setSession } = useSession();
  const [cogHistory, setCogHistory] = useState([]);
  const [riskHistory, setRiskHistory] = useState([]);
  const [shapData, setShapData] = useState(null);
  const [status, setStatus] = useState('loading');   // loading | live | waiting | error
  const pollRef = useRef(null);

  /* Set this session as globally active */
  useEffect(() => {
    if (UUID_RE.test(sessionId)) setSession(sessionId);
  }, [sessionId, setSession]);

  /* If sessionId isn't a valid UUID, skip API calls */
  const validSession = UUID_RE.test(sessionId);

  const fetchData = useCallback(async () => {
    if (!validSession) { setStatus('error'); return; }
    try {
      const [cog, risk] = await Promise.all([
        getCognitiveHistory(sessionId),
        getRiskHistory(sessionId),
      ]);
      const cogArr = Array.isArray(cog) ? cog : [];
      const riskArr = Array.isArray(risk) ? risk : [];
      setCogHistory(cogArr);
      setRiskHistory(riskArr);
      // If both arrays are empty, session exists but has no data yet
      setStatus(cogArr.length === 0 && riskArr.length === 0 ? 'waiting' : 'live');
      // Fetch SHAP explainability
      if (cogArr.length > 0) {
        getExplainability(sessionId).then(setShapData).catch(() => {});
      }
    } catch (err) {
      if (err?.response?.status === 404) {
        setStatus('waiting');
      } else {
        setStatus('error');
      }
    }
  }, [sessionId]);

  /* Initial REST fetch for hydration */
  useEffect(() => {
    setStatus('loading');
    setCogHistory([]);
    setRiskHistory([]);
    fetchData();
  }, [sessionId]); // eslint-disable-line react-hooks/exhaustive-deps

  /* WebSocket subscriptions — replace setInterval polling */
  useWebSocket(
    validSession ? `/topic/session/${sessionId}/cognitive-history` : null,
    useCallback((entry) => {
      setCogHistory((prev) => {
        const next = [...prev, entry];
        // Refresh SHAP every 5th frame to avoid flooding
        if (next.length % 5 === 0) {
          getExplainability(sessionId).then(setShapData).catch(() => {});
        }
        return next;
      });
      setStatus('live');
    }, [sessionId])
  );

  useWebSocket(
    validSession ? `/topic/session/${sessionId}/risk-history` : null,
    useCallback((entry) => {
      setRiskHistory((prev) => [...prev, entry]);
      setStatus('live');
    }, [])
  );

  /* ── Derived chart data ── */
  const smoothedLoads = cogHistory.map((c) => c.smoothedLoad ?? 0);
  const expertLoads   = cogHistory.map((c) => c.expertComputedLoad ?? 0);
  const mlLoads       = cogHistory.map((c) => c.mlPredictedLoad ?? 0);
  const errorProbs    = cogHistory.map((c) => (c.errorProbability ?? 0) * 100);
  const fatigueData   = cogHistory.map((c) => c.fatigueTrendSlope ?? 0);
  const riskLevels    = riskHistory.map((r) => RISK_ORDER[r.riskLevel] ?? 0);
  const confidences   = cogHistory.map((c) => (c.confidenceScore ?? 0) * 100);
  const swissCheese   = cogHistory.map((c) => (c.swissCheeseAlignmentScore ?? 0) * 100);

  /* ── Risk distribution ── */
  const riskCounts = riskHistory.reduce(
    (acc, r) => { acc[r.riskLevel] = (acc[r.riskLevel] || 0) + 1; return acc; },
    { LOW: 0, MEDIUM: 0, HIGH: 0, CRITICAL: 0 }
  );
  const riskTotal = riskHistory.length || 1;

  /* ── Summary stats ── */
  const avgLoad  = smoothedLoads.length > 0 ? (smoothedLoads.reduce((a, b) => a + b, 0) / smoothedLoads.length).toFixed(1) : '—';
  const peakLoad = smoothedLoads.length > 0 ? Math.max(...smoothedLoads).toFixed(1) : '—';
  const avgConf  = confidences.length > 0 ? (confidences.reduce((a, b) => a + b, 0) / confidences.length).toFixed(1) : '—';
  const curLoad  = smoothedLoads.length > 0 ? smoothedLoads[smoothedLoads.length - 1].toFixed(1) : '—';

  /* ── Loading / waiting states ── */
  if (status === 'loading' || status === 'waiting') {
    const invalid = !validSession;
    return (
      <div className="cockpit-wrapper">
        <div className="cockpit-frame" style={{ backgroundImage: `url(${analyticsBg})` }}>
          <div className="screen-overlay" style={{ ...S.C, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <div style={{ textAlign: 'center' }}>
              <div className="digi" style={{ fontSize: '1.6em', color: invalid ? '#FF3333' : '#FFD700', marginBottom: '0.5em' }}>
                {invalid ? 'INVALID SESSION' : status === 'loading' ? 'CONNECTING…' : 'COLLECTING DATA'}
              </div>
              <div className="digi-label" style={{ fontSize: '1em', lineHeight: 1.5 }}>
                {!invalid && <>Session: {sessionId?.slice(0, 12)}…<br /></>}
                {invalid ? 'Go back to Home and select or create a valid session.'
                  : status === 'waiting' ? 'Analytics require at least one data frame — please wait…' : ''}
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
      <div className="cockpit-frame" style={{ backgroundImage: `url(${analyticsBg})` }}>

        {/* ═══ LEFT SCREEN: COGNITIVE LOAD TRENDS ═══ */}
        <div className="screen-overlay" style={{ ...S.L, padding: '6% 5% 4%' }}>
          <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            {/* Primary sparkline — smoothed loads */}
            <Spark data={smoothedLoads} color="#00FF41" height={55} />

            {/* Expert vs ML sub-sparklines */}
            <div style={{ display: 'flex', gap: '4%', marginTop: '3%' }}>
              <div style={{ flex: 1 }}>
                <div className="digi-label" style={{ fontSize: '0.95em', marginBottom: '0.2em' }}>EXPERT MODEL</div>
                <Spark data={expertLoads} color="#8FBC3A" height={30} />
              </div>
              <div style={{ flex: 1 }}>
                <div className="digi-label" style={{ fontSize: '0.95em', marginBottom: '0.2em' }}>ML MODEL</div>
                <Spark data={mlLoads} color="#00C2FF" height={30} />
              </div>
            </div>

            {/* Stat readouts */}
            <div style={{ marginTop: 'auto' }}>
              <Row label="SESSION"   value={sessionId?.slice(0, 12) ?? '—'} />
              <Row label="DATA PTS"  value={cogHistory.length} color="#00C2FF" />
              <Row label="AVG LOAD"  value={avgLoad} />
              <Row label="PEAK LOAD" value={peakLoad} color="#FF6B35" />
              <Row label="CURRENT"   value={curLoad} />
            </div>
          </div>
        </div>

        {/* ═══ CENTER SCREEN: RISK ANALYSIS ═══ */}
        <div className="screen-overlay" style={{ ...S.C, padding: '6% 5% 4%' }}>
          <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            {/* Risk timeline sparkline */}
            <Spark data={riskLevels} color="#FF6B35" height={50} />

            <div className="digi-label" style={{ fontSize: '1em', margin: '4% 0 2%' }}>
              RISK FRAMES: <span style={{ color: '#FF6B35' }}>{riskHistory.length}</span>
            </div>

            {/* Horizontal bar chart */}
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center', gap: '0.8em' }}>
              {['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((level) => {
                const pct = ((riskCounts[level] || 0) / riskTotal) * 100;
                return (
                  <div key={level}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: '0.15em' }}>
                      <span className="digi-label" style={{ fontSize: '1em' }}>{level}</span>
                      <span className="digi" style={{ fontSize: '1.2em', color: RISK_CLR[level] }}>
                        {riskCounts[level] || 0}
                      </span>
                    </div>
                    <div style={{ height: '0.4em', background: 'rgba(0,255,65,0.04)', borderRadius: '2px' }}>
                      <div style={{
                        height: '100%', borderRadius: '2px',
                        background: RISK_CLR[level],
                        width: `${pct}%`,
                        transition: 'width 0.5s ease-out',
                        boxShadow: `0 0 6px ${RISK_CLR[level]}44`,
                      }} />
                    </div>
                  </div>
                );
              })}
            </div>

            {/* Critical count callout */}
            <div style={{ textAlign: 'center', marginTop: 'auto' }}>
              <span className="digi-label" style={{ fontSize: '0.95em' }}>CRITICAL EVENTS </span>
              <span className="digi" style={{ fontSize: '1.7em', color: riskCounts.CRITICAL > 0 ? '#FF3333' : '#00FF41' }}>
                {riskCounts.CRITICAL || 0}
              </span>
            </div>

            {/* SHAP Feature Drivers */}
            {shapData?.available && shapData.featureContributions?.length > 0 && (
              <div style={{ marginTop: '4%' }}>
                <div className="digi-label" style={{ fontSize: '0.9em', marginBottom: '0.3em' }}>SHAP DRIVERS</div>
                {shapData.featureContributions.slice(0, 4).map((fc) => {
                  const maxAbs = Math.max(...shapData.featureContributions.map((f) => Math.abs(f.shapValue)), 0.01);
                  const pct = Math.min((Math.abs(fc.shapValue) / maxAbs) * 100, 100);
                  const clr = fc.shapValue > 0 ? '#FF6B35' : '#00C2FF';
                  return (
                    <div key={fc.feature} style={{ marginBottom: '0.25em' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.7em' }}>
                        <span className="digi-label" style={{ fontSize: '1em', textTransform: 'uppercase' }}>
                          {fc.feature.replace(/_/g, ' ').slice(0, 14)}
                        </span>
                        <span className="digi" style={{ fontSize: '1em', color: clr }}>
                          {fc.shapValue > 0 ? '+' : ''}{fc.shapValue.toFixed(2)}
                        </span>
                      </div>
                      <div style={{ height: '0.25em', background: 'rgba(0,255,65,0.04)', borderRadius: '2px' }}>
                        <div style={{
                          height: '100%', borderRadius: '2px', background: clr,
                          width: `${pct}%`, transition: 'width 0.4s ease-out',
                          boxShadow: `0 0 4px ${clr}44`,
                        }} />
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        {/* ═══ RIGHT SCREEN: ML PERFORMANCE ═══ */}
        <div className="screen-overlay" style={{ ...S.R, padding: '6% 6% 4%' }}>
          <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>

            {/* Error Probability */}
            <div className="digi-label" style={{ fontSize: '0.95em', marginBottom: '0.2em' }}>ERROR PROBABILITY</div>
            <Spark data={errorProbs} color="#DC2626" height={28} />
            <div style={{ display: 'flex', gap: '10%', margin: '2% 0 5%' }}>
              <MiniStat label="CURRENT" value={errorProbs.length ? `${errorProbs[errorProbs.length - 1].toFixed(1)}%` : '—'} color="#DC2626" />
              <MiniStat label="PEAK" value={errorProbs.length ? `${Math.max(...errorProbs).toFixed(1)}%` : '—'} color="#DC2626" />
            </div>

            {/* Fatigue Slope */}
            <div className="digi-label" style={{ fontSize: '0.95em', marginBottom: '0.2em' }}>FATIGUE SLOPE</div>
            <Spark data={fatigueData} color="#F59E0B" height={28} />
            <div style={{ display: 'flex', gap: '10%', margin: '2% 0 5%' }}>
              <MiniStat label="LATEST" value={fatigueData.length ? fatigueData[fatigueData.length - 1].toFixed(4) : '—'} color="#F59E0B" />
              <MiniStat label="MAX" value={fatigueData.length ? Math.max(...fatigueData).toFixed(4) : '—'} color="#F59E0B" />
            </div>

            {/* ML Confidence */}
            <div className="digi-label" style={{ fontSize: '0.95em', marginBottom: '0.2em' }}>ML CONFIDENCE</div>
            <Spark data={confidences} color="#00C2FF" height={28} />
            <div style={{ display: 'flex', gap: '8%', marginTop: '2%' }}>
              <MiniStat label="CURRENT" value={confidences.length ? `${confidences[confidences.length - 1].toFixed(1)}%` : '—'} color="#00C2FF" />
              <MiniStat label="MIN" value={confidences.length ? `${Math.min(...confidences).toFixed(1)}%` : '—'} color="#F59E0B" />
              <MiniStat label="AVG" value={`${avgConf}%`} color="#00C2FF" />
            </div>

            {/* Swiss Cheese Alignment */}
            <div className="digi-label" style={{ fontSize: '0.95em', marginBottom: '0.2em', marginTop: '4%' }}>SWISS CHEESE</div>
            <Spark data={swissCheese} color="#E879F9" height={28} />
            <div style={{ display: 'flex', gap: '10%', marginTop: '2%' }}>
              <MiniStat label="CURRENT" value={swissCheese.length ? `${swissCheese[swissCheese.length - 1].toFixed(0)}%` : '—'} color="#E879F9" />
              <MiniStat label="MAX" value={swissCheese.length ? `${Math.max(...swissCheese).toFixed(0)}%` : '—'} color="#FF3333" />
            </div>
          </div>
        </div>
      </div>

      {/* Error ribbon */}
      {status === 'error' && (
        <div style={{
          position: 'fixed', bottom: 0, left: 0, right: 0,
          background: 'rgba(245,158,11,0.9)', color: '#000',
          textAlign: 'center', padding: '6px',
          fontFamily: "'Share Tech Mono', monospace",
          fontSize: '0.8rem', fontWeight: 'bold', zIndex: 100,
        }}>
          ⚠ CONNECTION INTERRUPTED — retrying every 3s…
        </div>
      )}
    </div>
  );
}

/* ═══════════════════════════════════════════════
   Inline SVG Sparkline — cockpit display style
   Renders as a glowing polyline on the dark screen
   ═══════════════════════════════════════════════ */
function Spark({ data, color = '#00FF41', height = 50 }) {
  if (!data || data.length < 2) {
    return (
      <div style={{ height, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <span className="digi" style={{ fontSize: '1.05em', opacity: 0.3 }}>AWAITING DATA</span>
      </div>
    );
  }

  const w = 300, h = height;
  const min = Math.min(...data);
  const max = Math.max(...data);
  const range = max - min || 1;
  const pad = 2;

  const points = data.map((v, i) => {
    const x = (i / (data.length - 1)) * (w - pad * 2) + pad;
    const y = h - pad - ((v - min) / range) * (h - pad * 2);
    return `${x},${y}`;
  }).join(' ');

  return (
    <svg viewBox={`0 0 ${w} ${h}`} style={{ width: '100%', height }} preserveAspectRatio="none">
      {/* Glow layer */}
      <polyline points={points} fill="none" stroke={color} strokeWidth="4" opacity="0.08" />
      {/* Main line */}
      <polyline points={points} fill="none" stroke={color} strokeWidth="1.5" opacity="0.85" />
    </svg>
  );
}

/* ═══════════════════════════════════════════════
   Stat Row — label / value key-pair (left-aligned)
   ═══════════════════════════════════════════════ */
function Row({ label, value, color }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', lineHeight: 1.7 }}>
      <span className="digi-label">{label}</span>
      <span className="digi-value" style={color ? { color } : undefined}>{value}</span>
    </div>
  );
}

/* ═══════════════════════════════════════════════
   Mini Stat — compact label + value (vertical)
   ═══════════════════════════════════════════════ */
function MiniStat({ label, value, color }) {
  return (
    <div>
      <div className="digi-label" style={{ fontSize: '0.9em' }}>{label}</div>
      <div className="digi" style={{ fontSize: '1.15em', ...(color && { color }) }}>{value}</div>
    </div>
  );
}

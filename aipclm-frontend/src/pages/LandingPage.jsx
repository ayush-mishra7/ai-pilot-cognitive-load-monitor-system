import { useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import landingBg from '../assets/landing-page.png';

export default function LandingPage() {
  const navigate = useNavigate();
  const featuresRef = useRef(null);

  const features = [
    {
      icon: '🧠',
      title: 'Cognitive Load AI',
      desc: 'Expert + ML hybrid model computing real-time pilot workload with confidence-gated fusion.',
    },
    {
      icon: '🛩️',
      title: '6-Phase Simulation',
      desc: 'Deterministic telemetry across TAKEOFF → CLIMB → CRUISE → DESCENT → APPROACH → LANDING.',
    },
    {
      icon: '🧀',
      title: 'Swiss Cheese Model',
      desc: '4-barrier safety model inspired by James Reason — fatigue, errors, turbulence, physiology.',
    },
    {
      icon: '📊',
      title: 'Real-Time Dashboard',
      desc: 'Glass cockpit UI with radial gauges, risk indicators, and live recommendation feeds.',
    },
    {
      icon: '⚛️',
      title: 'Atomic Pipeline',
      desc: '5-stage transactional pipeline — Telemetry → Cognitive → Risk → Recommendation → Persist.',
    },
    {
      icon: '🤖',
      title: 'ML Inference Service',
      desc: 'FastAPI microservice with 3s timeout, automatic fallback, and confidence scoring.',
    },
  ];

  return (
    <div className="landing-page">
      {/* Background image — blurred + darkened */}
      <div
        className="landing-bg"
        style={{ backgroundImage: `url(${landingBg})` }}
      />
      {/* Gradient fade overlays */}
      <div className="landing-fade" />

      {/* ═══ Hero Section ═══ */}
      <section className="landing-hero">
        <div className="landing-hero__inner">
          {/* Chip badge */}
          <div className="landing-chip">
            <div className="landing-chip__dot" />
            <span>AI-Powered Aviation Safety</span>
          </div>

          <h1 className="landing-h1">
            Pilot Cognitive
            <br />
            <span className="landing-h1__accent">Load Monitor</span>
          </h1>

          <p className="landing-desc">
            Real-time simulation and monitoring of pilot cognitive workload using
            expert systems, ML inference, and multi-barrier risk assessment.
          </p>

          <div className="landing-cta-row">
            <button
              className="landing-btn landing-btn--primary"
              onClick={() => navigate('/login')}
            >
              Get Started
            </button>
            <button
              className="landing-btn landing-btn--ghost"
              onClick={() =>
                featuresRef.current?.scrollIntoView({ behavior: 'smooth' })
              }
            >
              Learn More
            </button>
          </div>
        </div>

        {/* Scroll indicator */}
        <div className="landing-scroll">
          <div className="landing-scroll__track">
            <div className="landing-scroll__thumb" />
          </div>
        </div>
      </section>

      {/* ═══ Features Section ═══ */}
      <section ref={featuresRef} className="landing-features">
        <div className="landing-features__inner">
          <h2 className="landing-h2">System Capabilities</h2>
          <p className="landing-features__sub">
            Built for aviation safety researchers, human factors engineers, and
            cockpit design teams.
          </p>

          <div className="landing-grid">
            {features.map((f, i) => (
              <div key={i} className="landing-card">
                <div className="landing-card__icon">{f.icon}</div>
                <h3 className="landing-card__title">{f.title}</h3>
                <p className="landing-card__desc">{f.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ═══ CTA Footer ═══ */}
      <section className="landing-footer-cta">
        <div className="landing-footer-cta__box">
          <h3 className="landing-footer-cta__title">
            Ready to Monitor Cognitive Load?
          </h3>
          <p className="landing-footer-cta__desc">
            Start a simulation session and watch the AI pipeline in action.
          </p>
          <button
            className="landing-btn landing-btn--accent"
            onClick={() => navigate('/login')}
          >
            Launch Dashboard
          </button>
        </div>
      </section>
    </div>
  );
}

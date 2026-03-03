import { useRef, useEffect, useMemo } from 'react';

/**
 * Compact sparkline — thin line chart for inline trend display.
 * Props: data (number[]), color (hex), width, height
 */
export default function Sparkline({
  data = [],
  color = '#6B8E23',
  width = 120,
  height = 28,
}) {
  const canvasRef = useRef(null);
  const safeData = useMemo(() => (data.length > 1 ? data : [0, 0]), [data]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const dpr = window.devicePixelRatio || 1;
    canvas.width = width * dpr;
    canvas.height = height * dpr;
    ctx.scale(dpr, dpr);
    ctx.clearRect(0, 0, width, height);

    const max = Math.max(...safeData, 1);
    const min = Math.min(...safeData, 0);
    const range = max - min || 1;
    const pad = 2;
    const gw = width - pad * 2;
    const gh = height - pad * 2;

    const pts = safeData.map((v, i) => ({
      x: pad + (i / (safeData.length - 1)) * gw,
      y: pad + gh - ((v - min) / range) * gh,
    }));

    // fill
    const grad = ctx.createLinearGradient(0, 0, 0, height);
    grad.addColorStop(0, color + '20');
    grad.addColorStop(1, color + '00');
    ctx.beginPath();
    ctx.moveTo(pts[0].x, height);
    pts.forEach((p) => ctx.lineTo(p.x, p.y));
    ctx.lineTo(pts[pts.length - 1].x, height);
    ctx.fillStyle = grad;
    ctx.fill();

    // line
    ctx.beginPath();
    pts.forEach((p, i) => (i === 0 ? ctx.moveTo(p.x, p.y) : ctx.lineTo(p.x, p.y)));
    ctx.strokeStyle = color;
    ctx.lineWidth = 1.5;
    ctx.lineJoin = 'round';
    ctx.stroke();
  }, [safeData, color, width, height]);

  return (
    <canvas
      ref={canvasRef}
      style={{ width, height, display: 'block' }}
    />
  );
}

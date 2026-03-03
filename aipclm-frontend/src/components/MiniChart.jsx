import { useRef, useEffect, useMemo } from 'react';

/**
 * Simple line chart using canvas — olive/cyan themed.
 * Props: data (array of numbers), label, color, width, height
 */
export default function MiniChart({
  data = [],
  label = '',
  color = '#6B8E23',
  width = 320,
  height = 140,
}) {
  const canvasRef = useRef(null);

  const safeData = useMemo(
    () => (data.length > 0 ? data : [0]),
    [data]
  );

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const dpr = window.devicePixelRatio || 1;
    canvas.width = width * dpr;
    canvas.height = height * dpr;
    ctx.scale(dpr, dpr);

    ctx.clearRect(0, 0, width, height);

    const maxVal = Math.max(...safeData, 1);
    const minVal = Math.min(...safeData, 0);
    const range = maxVal - minVal || 1;
    const pad = 16;
    const graphW = width - pad * 2;
    const graphH = height - pad * 2 - 20;

    // Grid lines
    ctx.strokeStyle = 'rgba(0,0,0,0.04)';
    ctx.lineWidth = 1;
    for (let i = 0; i <= 4; i++) {
      const y = pad + 10 + (graphH / 4) * i;
      ctx.beginPath();
      ctx.moveTo(pad, y);
      ctx.lineTo(pad + graphW, y);
      ctx.stroke();
    }

    // Gradient fill
    const gradient = ctx.createLinearGradient(0, pad + 10, 0, pad + 10 + graphH);
    gradient.addColorStop(0, color + '30');
    gradient.addColorStop(1, color + '05');

    const points = safeData.map((v, i) => ({
      x: pad + (i / Math.max(safeData.length - 1, 1)) * graphW,
      y: pad + 10 + graphH - ((v - minVal) / range) * graphH,
    }));

    // Fill area
    ctx.beginPath();
    ctx.moveTo(points[0].x, pad + 10 + graphH);
    points.forEach((p) => ctx.lineTo(p.x, p.y));
    ctx.lineTo(points[points.length - 1].x, pad + 10 + graphH);
    ctx.fillStyle = gradient;
    ctx.fill();

    // Line
    ctx.beginPath();
    points.forEach((p, i) => (i === 0 ? ctx.moveTo(p.x, p.y) : ctx.lineTo(p.x, p.y)));
    ctx.strokeStyle = color;
    ctx.lineWidth = 2;
    ctx.lineJoin = 'round';
    ctx.stroke();

    // Dots on last point
    const last = points[points.length - 1];
    ctx.beginPath();
    ctx.arc(last.x, last.y, 4, 0, Math.PI * 2);
    ctx.fillStyle = color;
    ctx.fill();
    ctx.beginPath();
    ctx.arc(last.x, last.y, 2, 0, Math.PI * 2);
    ctx.fillStyle = '#fff';
    ctx.fill();

    // Label
    ctx.fillStyle = '#888';
    ctx.font = `600 ${10}px Inter, system-ui, sans-serif`;
    ctx.textAlign = 'left';
    ctx.fillText(label, pad, pad + 4);
  }, [safeData, color, width, height, label]);

  return (
    <canvas
      ref={canvasRef}
      style={{ width, height }}
      className="smooth-transform"
    />
  );
}

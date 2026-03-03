import { useRef, useEffect } from 'react';

/**
 * Radial gauge component using canvas for smooth rendering.
 * Props: value (0-100), label, color, size
 */
export default function RadialGauge({
  value = 0,
  label = '',
  color = '#6B8E23',
  size = 180,
}) {
  const canvasRef = useRef(null);
  const animatedValue = useRef(0);
  const animationFrame = useRef(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const dpr = window.devicePixelRatio || 1;
    canvas.width = size * dpr;
    canvas.height = size * dpr;
    ctx.scale(dpr, dpr);

    const center = size / 2;
    const radius = size * 0.38;
    const lineWidth = size * 0.06;

    function draw(current) {
      ctx.clearRect(0, 0, size, size);

      // Background arc
      ctx.beginPath();
      ctx.arc(center, center, radius, 0.75 * Math.PI, 2.25 * Math.PI);
      ctx.strokeStyle = 'rgba(0,0,0,0.08)';
      ctx.lineWidth = lineWidth;
      ctx.lineCap = 'round';
      ctx.stroke();

      // Value arc
      const endAngle = 0.75 * Math.PI + (current / 100) * 1.5 * Math.PI;
      ctx.beginPath();
      ctx.arc(center, center, radius, 0.75 * Math.PI, endAngle);
      ctx.strokeStyle = color;
      ctx.lineWidth = lineWidth;
      ctx.lineCap = 'round';
      ctx.stroke();

      // Center text
      ctx.fillStyle = '#0a0a0a';
      ctx.font = `bold ${size * 0.18}px Inter, system-ui, sans-serif`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(Math.round(current), center, center - 4);

      // Label
      ctx.fillStyle = '#555';
      ctx.font = `600 ${size * 0.08}px Inter, system-ui, sans-serif`;
      ctx.fillText(label, center, center + size * 0.16);
    }

    function animate() {
      const diff = value - animatedValue.current;
      if (Math.abs(diff) < 0.3) {
        animatedValue.current = value;
        draw(value);
        return;
      }
      animatedValue.current += diff * 0.08;
      draw(animatedValue.current);
      animationFrame.current = requestAnimationFrame(animate);
    }

    animationFrame.current = requestAnimationFrame(animate);

    return () => {
      if (animationFrame.current) cancelAnimationFrame(animationFrame.current);
    };
  }, [value, color, size, label]);

  return (
    <canvas
      ref={canvasRef}
      style={{ width: size, height: size }}
      className="smooth-transform"
    />
  );
}
